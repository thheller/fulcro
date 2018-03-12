(ns fulcro.client.alpha.localized-dom
  (:refer-clojure :exclude [map meta time])
  #?(:cljs (:require-macros [fulcro.client.alpha.localized-dom]))
  (:require
    [fulcro.client.primitives :as prim]
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    #?@(:clj  [
    [fulcro.client.impl.protocols :as p]
    [clojure.future :refer :all]
    [clojure.core.reducers :as r]
    [fulcro.util :as util]
    [fulcro.checksums :as chk]]
        :cljs [[cljsjs.react]
               [cljsjs.react.dom]
               [cljsjs.react.dom.server]
               [goog.object :as gobj]]))
  #?(:clj
     (:import (cljs.tagged_literals JSValue))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLJC CSS Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn remove-separators [s]
  (when s
    (str/replace s #"^[.#$]" "")))

(defn get-tokens [k]
  (re-seq #"[#.$]?[^#.$]+" (name k)))

(defn parse
  "Parse CSS shorthand keyword and return map of id/classes.

  (parse :.klass3#some-id.klass1.klass2)
  => {:id        \"some-id\"
      :classes [\"klass3\" \"klass1\" \"klass2\"]}"
  [k]
  (if k
    (let [tokens         (get-tokens k)
          id             (->> tokens (filter #(re-matches #"^#.*" %)) first)
          classes        (->> tokens (filter #(re-matches #"^\..*" %)))
          global-classes (into []
                           (comp
                             (filter #(re-matches #"^[$].*" %))
                             (clojure.core/map (fn [k] (-> k
                                                         name
                                                         (str/replace "$" "")))))
                           tokens)
          sanitized-id   (remove-separators id)]
      (when-not (re-matches #"^(\.[^.#$]+|#[^.#$]+|[$][^.#$]+)+$" (name k))
        (throw (ex-info "Invalid style keyword. It contains something other than classnames and IDs." {})))
      (cond-> {:global-classes global-classes
               :classes        (into [] (keep remove-separators classes))}
        sanitized-id (assoc :id sanitized-id)))
    {}))

(defn- combined-classes
  "Takes a sequence of classname strings and a string with existing classes. Returns a string of these properly joined.

  classes-str can be nil or and empty string, and classes-seq can be nil or empty."
  [classes-seq classes-str]
  (str/join " " (if (seq classes-str) (conj classes-seq classes-str) classes-seq)))

(letfn [(pget [p nm dflt] (cond
                            #?@(:clj [(instance? JSValue p) (get-in p [:val nm] dflt)])
                            (map? p) (get p nm dflt)
                            #?@(:cljs [(object? p) (gobj/get p (name nm) dflt)])))
        (passoc [p nm v] (cond
                           #?@(:clj [(instance? JSValue p) (JSValue. (assoc (.-val p) nm v))])
                           (map? p) (assoc p nm v)
                           #?@(:cljs [(object? p) (do (gobj/set p (name nm) v) p)])))
        (pdissoc [p nm] (cond
                          #?@(:clj [(instance? JSValue p) (JSValue. (dissoc (.-val p) nm))])
                          (map? p) (dissoc p nm)
                          #?@(:cljs [(object? p) (do (gobj/remove p (name nm)) p)])))
        (strip-prefix [s] (str/replace s #"^[:.#$]*" ""))]
  (defn fold-in-classes
    "Update the :className prop in the given props to include the classes in the :classes entry of props. Works on js objects and CLJ maps as props.
    If using js props, they must be mutable."
    [props component]
    (if-let [extra-classes (pget props :classes nil)]
      (let [old-classes (pget props :className "")]
        (pdissoc
          (if component
            (let [clz         (prim/react-type component)
                  new-classes (combined-classes (clojure.core/map (fn [c]
                                                                    (let [c (some-> c name)]
                                                                      (cond
                                                                        (nil? c) ""
                                                                        (str/starts-with? c ".") (fulcro.client.css/local-class clz (strip-prefix c))
                                                                        (str/starts-with? c "$") (strip-prefix c)
                                                                        :else c))) extra-classes) old-classes)]
              (passoc props :className new-classes))
            (let [new-classes (combined-classes (clojure.core/map strip-prefix extra-classes) old-classes)]
              (passoc props :className new-classes)))
          :classes))
      props)))

(defn combine
  "Combine a hiccup-style keyword with props that are either a JS or CLJS map."
  [props kw]
  (let [{:keys [global-classes classes id] :or {classes []}} (parse kw)
        classes (vec (concat
                       (if prim/*parent*
                         (clojure.core/map #(fulcro.client.css/local-class (prim/react-type prim/*parent*) %) classes)
                         classes)
                       global-classes))]
    (fold-in-classes
      (if #?(:clj false :cljs (or (nil? props) (object? props)))
        #?(:clj  props
           :cljs (let [props            (gobj/clone props)
                       existing-classes (gobj/get props "className")]
                   (when (seq classes) (gobj/set props "className" (combined-classes classes existing-classes)))
                   (when id (gobj/set props "id" id))
                   props))
        (let [existing-classes (:className props)]
          (cond-> (or props {})
            (seq classes) (assoc :className (combined-classes classes existing-classes))
            id (assoc :id id))))
      prim/*parent*)))

(declare tags
  a
  abbr
  address
  area
  article
  aside
  audio
  b
  base
  bdi
  bdo
  big
  blockquote
  body
  br
  button
  canvas
  caption
  cite
  code
  col
  colgroup
  data
  datalist
  dd
  del
  details
  dfn
  dialog
  div
  dl
  dt
  em
  embed
  fieldset
  figcaption
  figure
  footer
  form
  h1
  h2
  h3
  h4
  h5
  h6
  head
  header
  hr
  html
  i
  iframe
  img
  ins
  input
  textarea
  select
  option
  kbd
  keygen
  label
  legend
  li
  link
  main
  map
  mark
  menu
  menuitem
  meta
  meter
  nav
  noscript
  object
  ol
  optgroup
  output
  p
  param
  picture
  pre
  progress
  q
  rp
  rt
  ruby
  s
  samp
  script
  section
  small
  source
  span
  strong
  style
  sub
  summary
  sup
  table
  tbody
  td
  tfoot
  th
  thead
  time
  title
  tr
  track
  u
  ul
  var
  video
  wbr

  ;; svg
  circle
  clipPath
  ellipse
  g
  line
  mask
  path
  pattern
  polyline
  rect
  svg
  text
  defs
  linearGradient
  polygon
  radialGradient
  stop
  tspan)

(def tags
  '[a
    abbr
    address
    area
    article
    aside
    audio
    b
    base
    bdi
    bdo
    big
    blockquote
    body
    br
    button
    canvas
    caption
    cite
    code
    col
    colgroup
    data
    datalist
    dd
    del
    details
    dfn
    dialog
    div
    dl
    dt
    em
    embed
    fieldset
    figcaption
    figure
    footer
    form
    h1
    h2
    h3
    h4
    h5
    h6
    head
    header
    hr
    html
    i
    iframe
    img
    ins
    kbd
    keygen
    label
    legend
    li
    link
    main
    map
    mark
    menu
    menuitem
    meta
    meter
    nav
    noscript
    object
    ol
    optgroup
    output
    p
    param
    picture
    pre
    progress
    q
    rp
    rt
    ruby
    s
    samp
    script
    section
    small
    source
    span
    strong
    style
    sub
    summary
    sup
    table
    tbody
    td
    tfoot
    th
    thead
    time
    title
    tr
    track
    u
    ul
    var
    video
    wbr

    ;; svg
    circle
    clipPath
    ellipse
    g
    line
    mask
    path
    pattern
    polyline
    rect
    svg
    text
    defs
    linearGradient
    polygon
    radialGradient
    stop
    tspan
    input
    select
    option
    textarea])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server-side rendering
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (def supported-attrs
     #{;; HTML
       "accept" "acceptCharset" "accessKey" "action" "allowFullScreen" "allowTransparency" "alt"
       "async" "autoComplete" "autoFocus" "autoPlay" "capture" "cellPadding" "cellSpacing" "challenge"
       "charSet" "checked" "cite" "classID" "className" "colSpan" "cols" "content" "contentEditable"
       "contextMenu" "controls" "coords" "crossOrigin" "data" "dateTime" "default" "defer" "dir"
       "disabled" "download" "draggable" "encType" "form" "formAction" "formEncType" "formMethod"
       "formNoValidate" "formTarget" "frameBorder" "headers" "height" "hidden" "high" "href" "hrefLang"
       "htmlFor" "httpEquiv" "icon" "id" "inputMode" "integrity" "is" "keyParams" "keyType" "kind" "label"
       "lang" "list" "loop" "low" "manifest" "marginHeight" "marginWidth" "max" "maxLength" "media"
       "mediaGroup" "method" "min" "minLength" "multiple" "muted" "name" "noValidate" "nonce" "open"
       "optimum" "pattern" "placeholder" "poster" "preload" "profile" "radioGroup" "readOnly" "referrerPolicy"
       "rel" "required" "reversed" "role" "rowSpan" "rows" "sandbox" "scope" "scoped" "scrolling" "seamless" "selected"
       "shape" "size" "sizes" "span" "spellCheck" "src" "srcDoc" "srcLang" "srcSet" "start" "step" "style" "summary"
       "tabIndex" "target" "title" "type" "useMap" "value" "width" "wmode" "wrap"
       ;; RDF
       "about" "datatype" "inlist" "prefix" "property" "resource" "typeof" "vocab"
       ;; SVG
       "accentHeight" "accumulate" "additive" "alignmentBaseline" "allowReorder" "alphabetic"
       "amplitude" "ascent" "attributeName" "attributeType" "autoReverse" "azimuth"
       "baseFrequency" "baseProfile" "bbox" "begin" "bias" "by" "calcMode" "clip"
       "clipPathUnits" "contentScriptType" "contentStyleType" "cursor" "cx" "cy" "d"
       "decelerate" "descent" "diffuseConstant" "direction" "display" "divisor" "dur"
       "dx" "dy" "edgeMode" "elevation" "end" "exponent" "externalResourcesRequired"
       "fill" "filter" "filterRes" "filterUnits" "focusable" "format" "from" "fx" "fy"
       "g1" "g2" "glyphRef" "gradientTransform" "gradientUnits" "hanging" "ideographic"
       "in" "in2" "intercept" "k" "k1" "k2" "k3" "k4" "kernelMatrix" "kernelUnitLength"
       "kerning" "keyPoints" "keySplines" "keyTimes" "lengthAdjust" "limitingConeAngle"
       "local" "markerHeight" "markerUnits" "markerWidth" "mask" "maskContentUnits"
       "maskUnits" "mathematical" "mode" "numOctaves" "offset" "opacity" "operator"
       "order" "orient" "orientation" "origin" "overflow" "pathLength" "patternContentUnits"
       "patternTransform" "patternUnits" "points" "pointsAtX" "pointsAtY" "pointsAtZ"
       "preserveAlpha" "preserveAspectRatio" "primitiveUnits" "r" "radius" "refX" "refY"
       "repeatCount" "repeatDur" "requiredExtensions" "requiredFeatures" "restart"
       "result" "rotate" "rx" "ry" "scale" "seed" "slope" "spacing" "specularConstant"
       "specularExponent" "speed" "spreadMethod" "startOffset" "stdDeviation" "stemh"
       "stemv" "stitchTiles" "string" "stroke" "surfaceScale" "systemLanguage" "tableValues"
       "targetX" "targetY" "textLength" "to" "transform" "u1" "u2" "unicode" "values"
       "version" "viewBox" "viewTarget" "visibility" "widths" "x" "x1" "x2" "xChannelSelector"
       "xmlns" "y" "y1" "y2" "yChannelSelector" "z" "zoomAndPan" "arabicForm" "baselineShift"
       "capHeight" "clipPath" "clipRule" "colorInterpolation" "colorInterpolationFilters"
       "colorProfile" "colorRendering" "dominantBaseline" "enableBackground" "fillOpacity"
       "fillRule" "floodColor" "floodOpacity" "fontFamily" "fontSize" "fontSizeAdjust"
       "fontStretch" "fontStyle" "fontVariant" "fontWeight" "glyphName" "glyphOrientationHorizontal"
       "glyphOrientationVertical" "horizAdvX" "horizOriginX" "imageRendering" "letterSpacing"
       "lightingColor" "markerEnd" "markerMid" "markerStart" "overlinePosition" "overlineThickness"
       "paintOrder" "panose1" "pointerEvents" "renderingIntent" "shapeRendering" "stopColor"
       "stopOpacity" "strikethroughPosition" "strikethroughThickness" "strokeDasharray"
       "strokeDashoffset" "strokeLinecap" "strokeLinejoin" "strokeMiterlimit" "strokeOpacity"
       "strokeWidth" "textAnchor" "textDecoration" "textRendering" "underlinePosition"
       "underlineThickness" "unicodeBidi" "unicodeRange" "unitsPerEm" "vAlphabetic"
       "vHanging" "vIdeographic" "vMathematical" "vectorEffect" "vertAdvY" "vertOriginX"
       "vertOriginY" "wordSpacing" "writingMode" "xHeight"

       "xlinkActuate" "xlinkArcrole" "xlinkHref" "xlinkRole" "xlinkShow" "xlinkTitle"
       "xlinkType" "xmlBase" "xmlnsXlink" "xmlLang" "xmlSpace"

       ;; Non-standard Properties
       "autoCapitalize" "autoCorrect" "autoSave" "color" "itemProp" "itemScope"
       "itemType" "itemID" "itemRef" "results" "security" "unselectable"

       ;; Special case
       "data-reactid" "data-reactroot"}))

#?(:clj
   (def no-suffix
     #{"animationIterationCount" "boxFlex" "boxFlexGroup" "boxOrdinalGroup"
       "columnCount" "fillOpacity" "flex" "flexGrow" "flexPositive" "flexShrink"
       "flexNegative" "flexOrder" "fontWeight" "lineClamp" "lineHeight" "opacity"
       "order" "orphans" "stopOpacity" "strokeDashoffset" "strokeOpacity"
       "strokeWidth" "tabSize" "widows" "zIndex" "zoom"}))

#?(:clj
   (def lower-case-attrs
     #{"accessKey" "allowFullScreen" "allowTransparency" "as" "autoComplete"
       "autoFocus" "autoPlay" "contentEditable" "contextMenu" "crossOrigin"
       "cellPadding" "cellSpacing" "charSet" "classID" "colSpan" "dateTime"
       "encType" "formAction" "formEncType" "formMethod" "formNoValidate"
       "formTarget" "frameBorder" "hrefLang" "inputMode" "keyParams"
       "keyType" "marginHeight" "marginWidth" "maxLength" "mediaGroup"
       "minLength" "noValidate" "playsInline" "radioGroup" "readOnly" "rowSpan"
       "spellCheck" "srcDoc" "srcLang" "srcSet" "tabIndex" "useMap"
       "autoCapitalize" "autoCorrect" "autoSave" "itemProp" "itemScope"
       "itemType" "itemID" "itemRef"}))

#?(:clj
   (def kebab-case-attrs
     #{"acceptCharset" "httpEquiv" "accentHeight" "alignmentBaseline" "arabicForm"
       "baselineShift" "capHeight" "clipPath" "clipRule" "colorInterpolation"
       "colorInterpolationFilters" "colorProfile" "colorRendering" "dominantBaseline"
       "enableBackground" "fillOpacity" "fillRule" "floodColor" "floodOpacity"
       "fontFamily" "fontSize" "fontSizeAdjust" "fontStretch" "fontStyle"
       "fontVariant" "fontWeight" "glyphName" "glyphOrientationHorizontal"
       "glyphOrientationVertical" "horizAdvX" "horizOriginX" "imageRendering"
       "letterSpacing" "lightingColor" "markerEnd" "markerMid" "markerStart"
       "overlinePosition" "overlineThickness" "paintOrder" "panose1" "pointerEvents"
       "renderingIntent" "shapeRendering" "stopColor" "stopOpacity" "strikethroughPosition"
       "strikethroughThickness" "strokeDasharray" "strokeDashoffset" "strokeLinecap"
       "strokeLinejoin" "strokeMiterlimit" "strokeOpacity" "strokeWidth" "textAnchor"
       "textDecoration" "textRendering" "underlinePosition" "underlineThickness"
       "unicodeBidi" "unicodeRange" "unitsPerEm" "vAlphabetic" "vHanging" "vIdeographic"
       "vMathematical" "vectorEffect" "vertAdvY" "vertOriginX" "vertOriginY" "wordSpacing"
       "writingMode" "xHeight"}))

#?(:clj
   (def colon-between-attrs
     #{"xlinkActuate" "xlinkArcrole" "xlinkHref" "xlinkRole" "xlinkShow" "xlinkTitle"
       "xlinkType" "xmlBase" "xmlnsXlink" "xmlLang" "xmlSpace"}))

#?(:clj (declare render-element!))

#?(:clj
   (defn append!
     ([^StringBuilder sb s0] (.append sb s0))
     ([^StringBuilder sb s0 s1]
      (.append sb s0)
      (.append sb s1))
     ([^StringBuilder sb s0 s1 s2]
      (.append sb s0)
      (.append sb s1)
      (.append sb s2))
     ([^StringBuilder sb s0 s1 s2 s3]
      (.append sb s0)
      (.append sb s1)
      (.append sb s2)
      (.append sb s3))
     ([^StringBuilder sb s0 s1 s2 s3 s4]
      (.append sb s0)
      (.append sb s1)
      (.append sb s2)
      (.append sb s3)
      (.append sb s4))
     ([^StringBuilder sb s0 s1 s2 s3 s4 & rest]
      (.append sb s0)
      (.append sb s1)
      (.append sb s2)
      (.append sb s3)
      (.append sb s4)
      (doseq [s rest]
        (.append sb s)))))

#?(:clj
   (defn escape-html ^String [^String s]
     (let [len (count s)]
       (loop [^StringBuilder sb nil
              i                 (int 0)]
         (if (< i len)
           (let [char (.charAt s i)
                 repl (case char
                        \& "&amp;"
                        \< "&lt;"
                        \> "&gt;"
                        \" "&quot;"
                        \' "&#x27;"
                        nil)]
             (if (nil? repl)
               (if (nil? sb)
                 (recur nil (inc i))
                 (recur (doto sb
                          (.append char))
                   (inc i)))
               (if (nil? sb)
                 (recur (doto (StringBuilder.)
                          (.append s 0 i)
                          (.append repl))
                   (inc i))
                 (recur (doto sb
                          (.append repl))
                   (inc i)))))
           (if (nil? sb) s (str sb)))))))

#?(:clj
   (defrecord Element [tag attrs react-key children]
     p/IReactDOMElement
     (-render-to-string [this react-id sb]
       (render-element! this react-id sb))))

#?(:clj
   (defrecord Text [s]
     p/IReactDOMElement
     (-render-to-string [this react-id sb]
       (assert (string? s))
       (append! sb (escape-html s)))))

#?(:clj
   (defrecord ReactText [text]
     p/IReactDOMElement
     (-render-to-string [this react-id sb]
       (assert (string? text))
       (append! sb "<!-- react-text: " @react-id " -->" (escape-html text) "<!-- /react-text -->")
       (vswap! react-id inc))))

#?(:clj
   (defrecord ReactEmpty []
     p/IReactDOMElement
     (-render-to-string [this react-id sb]
       (append! sb "<!-- react-empty: " @react-id " -->")
       (vswap! react-id inc))))

#?(:clj
   (defn text-node
     "HTML text node"
     [s]
     (map->Text {:s s})))

#?(:clj
   (defn react-text-node
     "HTML text node"
     [s]
     (map->ReactText {:text s})))

#?(:clj
   (defn- react-empty-node []
     (map->ReactEmpty {})))

#?(:clj
   (defn- render-component [c]
     (if (or (nil? c)
           (instance? fulcro.client.impl.protocols.IReactDOMElement c)
           (satisfies? p/IReactDOMElement c))
       c
       (recur (p/-render c)))))

#?(:clj
   (defn element
     "Creates a dom node."
     [{:keys [tag attrs react-key children] :as elem}]
     (assert (name tag))
     (assert (or (nil? attrs) (map? attrs)) (format "elem %s attrs invalid" elem))
     (let [children         (flatten children)
           child-node-count (count children)
           reduce-fn        (if (> child-node-count 1)
                              r/reduce
                              reduce)
           children         (reduce-fn
                              (fn [res c]
                                (let [c' (cond
                                           (or (instance? fulcro.client.impl.protocols.IReactDOMElement c)
                                             (satisfies? p/IReactDOMElement c))
                                           c

                                           (or (instance? fulcro.client.impl.protocols.IReactComponent c)
                                             (satisfies? p/IReactComponent c))
                                           (let [rendered (if-let [element (render-component c)]
                                                            element
                                                            (react-empty-node))]
                                             (assoc rendered :react-key
                                                             (some-> (:props c) :fulcro$reactKey)))

                                           (or (string? c) (number? c))
                                           (let [c (cond-> c (number? c) str)]
                                             (if (> child-node-count 1)
                                               (react-text-node c)
                                               (text-node c)))

                                           (nil? c) nil

                                           :else
                                           (throw (IllegalArgumentException. (str "Invalid child element: ") c)))]
                                  (cond-> res
                                    (some? c') (conj c'))))
                              [] children)]
       (map->Element {:tag       (name tag)
                      :attrs     attrs
                      :react-key react-key
                      :children  children}))))

#?(:clj
   (defn camel->other-case [^String sep]
     (fn ^String [^String s]
       (-> s
         (str/replace #"([A-Z0-9])" (str sep "$1"))
         str/lower-case))))

#?(:clj
   (def camel->kebab-case
     (camel->other-case "-")))

#?(:clj
   (def camel->colon-between
     (camel->other-case ":")))

#?(:clj
   (defn coerce-attr-key ^String [^String k]
     (cond
       (contains? lower-case-attrs k) (str/lower-case k)
       (contains? kebab-case-attrs k) (camel->kebab-case k)
       ;; special cases
       (= k "className") "class"
       (= k "htmlFor") "for"
       (contains? colon-between-attrs k) (camel->colon-between k)
       :else k)))

#?(:clj
   (defn render-xml-attribute! [sb name value]
     (let [name (coerce-attr-key (clojure.core/name name))]
       (append! sb " " name "=\""
         (cond-> value
           (string? value) escape-html) "\""))))

#?(:clj
   (defn normalize-styles! [sb styles]
     (letfn [(coerce-value [k v]
               (cond-> v
                 (string? v)
                 escape-html
                 (and (number? v)
                   (not (contains? no-suffix k))
                   (pos? v))
                 (str "px")))]
       (run! (fn [[k v]]
               (let [k (name k)]
                 (append! sb (camel->kebab-case k) ":" (coerce-value k v) ";")))
         styles))))

#?(:clj
   (defn render-styles! [sb styles]
     (when-not (empty? styles)
       (append! sb " style=\"")
       (normalize-styles! sb styles)
       (append! sb "\""))))

#?(:clj
   (defn render-attribute! [sb [key value]]
     (cond
       (or (fn? value)
         (not value))
       nil

       (identical? key :style)
       (render-styles! sb value)

       ;; TODO: not sure if we want to limit values to strings/numbers - António
       (and (or (contains? supported-attrs (name key))
              (.startsWith (name key) "data-"))
         (or (true? value) (string? value) (number? value)))
       (if (true? value)
         (append! sb " " (coerce-attr-key (name key)))
         (render-xml-attribute! sb key value))

       :else nil)))

;; some props assigned first in input and option. see:
;; https://github.com/facebook/react/blob/680685/src/renderers/dom/client/wrappers/ReactDOMOption.js#L108
;; https://github.com/facebook/react/blob/680685/src/renderers/dom/client/wrappers/ReactDOMInput.js#L63
#?(:clj
   (defn render-attr-map! [sb tag attrs]
     (letfn [(sorter [order]
               (fn [[k _]]
                 (get order k (->> (vals order)
                                (apply max)
                                inc))))]
       (let [attrs (cond->> attrs
                     (= tag "input") (sort-by (sorter {:type 0 :step 1
                                                       :min  2 :max 3}))
                     (= tag "option") (sort-by (sorter {:selected 0})))]
         (run! (partial render-attribute! sb) attrs)))))

#?(:clj
   (def ^{:doc     "A list of elements that must be rendered without a closing tag."
          :private true}
   void-tags
     #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
       "meta" "param" "source" "track" "wbr"}))

#?(:clj
   (defn render-unescaped-html! [sb m]
     (if-not (contains? m :__html)
       (throw (IllegalArgumentException. "`props.dangerouslySetInnerHTML` must be in the form `{:__html ...}`")))
     (when-let [html (:__html m)]
       (append! sb html))))

#?(:clj
   (defn container-tag?
     "Returns true if the tag has content or is not a void tag. In non-HTML modes,
      all contentless tags are assumed to be void tags."
     [tag content]
     (or content (and (not (void-tags tag))))))

#?(:clj
   (defn render-element!
     "Render a tag vector as a HTML element string."
     [{:keys [tag attrs children]} react-id ^StringBuilder sb]
     (append! sb "<" tag)
     (render-attr-map! sb tag attrs)
     (let [react-id-val @react-id]
       (when (= react-id-val 1)
         (append! sb " data-reactroot=\"\""))
       (append! sb " data-reactid=\"" react-id-val "\"")
       (vswap! react-id inc))
     (if (container-tag? tag (seq children))
       (do
         (append! sb ">")
         (if-let [html-map (:dangerouslySetInnerHTML attrs)]
           (render-unescaped-html! sb html-map)
           (run! #(p/-render-to-string % react-id sb) children))
         (append! sb "</" tag ">"))
       (append! sb "/>"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros and fns for SSR and Client DOM
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#?(:clj
   (defn clj-map->js-object
     "Recursively convert a map to a JS object. For use in macro expansion."
     [m]
     {:pre [(map? m)]}
     (JSValue. (into {}
                 (clojure.core/map (fn [[k v]]
                                     (cond
                                       (map? v) [k (clj-map->js-object v)]
                                       (vector? v) [k (mapv #(if (map? %) (clj-map->js-object %) %) v)]
                                       (symbol? v) [k `(cljs.core/clj->js ~v)]
                                       :else [k v])))
                 m))))

#?(:clj
   (s/def ::map-of-literals (fn [v]
                              (and (map? v)
                                (not-any? symbol? (tree-seq #(or (map? %) (vector? %) (seq? %)) seq v))))))

#?(:clj
   (s/def ::map-with-expr (fn [v]
                            (and (map? v)
                              (some #(or (symbol? %) (list? %)) (tree-seq #(or (map? %) (vector? %) (seq? %)) seq v))))))

#?(:clj
   (s/def ::dom-element-args
     (s/cat
       :css (s/? keyword?)
       :attrs (s/? (s/or :nil nil?
                     :map ::map-of-literals
                     :runtime-map ::map-with-expr
                     :js-object #(instance? JSValue %)
                     :symbol symbol?))
       :children (s/* (s/or :string string?
                        :number number?
                        :symbol symbol?
                        :nil nil?
                        :list list?)))))

#?(:clj
   (defn emit-tag [str-tag-name is-cljs? args]
     (let [conformed-args (util/conform! ::dom-element-args args)
           {attrs    :attrs
            children :children
            css      :css} conformed-args
           css-props      (if css `(fulcro.client.alpha.localized-dom/combine nil ~css) nil)
           children       (mapv second children)
           attrs-type     (or (first attrs) :nil)           ; attrs omitted == nil
           attrs-value    (or (second attrs) {})]
       (if is-cljs?
         (case attrs-type
           :js-object
           (let [attr-expr `(fulcro.client.alpha.localized-dom/combine ~attrs-value ~css)]
             `(fulcro.client.alpha.localized-dom/macro-create-element*
                ~(JSValue. (into [str-tag-name attr-expr] children))))

           :map
           (let [attr-expr (if (or css (contains? attrs-value :classes))
                             `(combine ~(clj-map->js-object attrs-value) ~css)
                             (clj-map->js-object attrs-value))]
             `(fulcro.client.alpha.localized-dom/macro-create-element* ~(JSValue. (into [str-tag-name attr-expr] children))))

           :runtime-map
           (let [attr-expr `(fulcro.client.alpha.localized-dom/combine ~(clj-map->js-object attrs-value) ~css)]
             `(fulcro.client.alpha.localized-dom/macro-create-element*
                ~(JSValue. (into [str-tag-name attr-expr] children))))

           :symbol
           `(fulcro.client.alpha.localized-dom/macro-create-element
              ~str-tag-name ~(into [attrs-value] children) ~css)

           ;; also used for MISSING props
           :nil
           `(fulcro.client.alpha.localized-dom/macro-create-element*
              ~(JSValue. (into [str-tag-name css-props] children)))

           ;; pure children
           `(fulcro.client.alpha.localized-dom/macro-create-element
              ~str-tag-name ~(JSValue. (into [attrs-value] children)) ~css))
         `(element {:tag       (quote ~(symbol str-tag-name))
                    :attrs     (-> ~attrs-value
                                 (dissoc :ref :key)
                                 (fulcro.client.alpha.localized-dom/combine ~css))
                    :react-key (:key ~attrs-value)
                    :children  ~children})))))

#?(:clj
   (defn gen-dom-macro [name]
     `(defmacro ~name [& args#]
        (let [tag#      ~(str name)
              is-cljs?# (boolean (:ns ~'&env))]
          (emit-tag tag# is-cljs?# args#)))))

#?(:clj
   (defmacro gen-dom-macros []
     `(do ~@(clojure.core/map gen-dom-macro tags))))

#?(:clj (gen-dom-macros))

#?(:clj
   (def key-escape-lookup
     {"=" "=0"
      ":" "=2"}))

;; preserves testability without having to compute checksums
#?(:clj
   (defn- render-to-str* ^StringBuilder [x]
     {:pre [(or (instance? fulcro.client.impl.protocols.IReactComponent x)
              (instance? fulcro.client.impl.protocols.IReactDOMElement x)
              (satisfies? p/IReactComponent x)
              (satisfies? p/IReactDOMElement x))]}
     (let [element (if-let [element (cond-> x
                                      (or (instance? fulcro.client.impl.protocols.IReactComponent x)
                                        (satisfies? p/IReactComponent x))
                                      render-component)]
                     element
                     (react-empty-node))
           sb      (StringBuilder.)]
       (p/-render-to-string element (volatile! 1) sb)
       sb)))

#?(:clj
   (defn render-to-str ^String [x]
     (let [sb (render-to-str* x)]
       (chk/assign-react-checksum sb)
       (str sb))))

#?(:clj
   (defn node
     "Returns the dom node associated with a component's React ref."
     ([component]
      {:pre [(or (instance? fulcro.client.impl.protocols.IReactComponent component)
               (satisfies? p/IReactComponent component))]}
      (p/-render component))
     ([component name]
      {:pre [(or (instance? fulcro.client.impl.protocols.IReactComponent component)
               (satisfies? p/IReactComponent component))]}
      (some-> @(:refs component) (get name) p/-render))))

#?(:clj
   (defn create-element
     "Create a DOM element for which there exists no corresponding function.
      Useful to create DOM elements not included in React.DOM. Equivalent
      to calling `js/React.createElement`"
     ([tag]
      (create-element tag nil))
     ([tag opts & children]
      (element {:tag       tag
                :attrs     (dissoc opts :ref :key)
                :react-key (:key opts)
                :children  children}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CLJS Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


#?(:cljs (defn render
           "Equivalent to React.render"
           [component el]
           (js/ReactDOM.render component el)))

#?(:cljs (defn render-to-str
           "Equivalent to React.renderToString"
           [c]
           (js/ReactDOMServer.renderToString c)))

#?(:cljs (defn node
           "Returns the dom node associated with a component's React ref."
           ([component]
            (js/ReactDOM.findDOMNode component))
           ([component name]
            (some-> (.-refs component) (gobj/get name) (js/ReactDOM.findDOMNode)))))

#?(:cljs (defn create-element
           "Create a DOM element for which there exists no corresponding function.
            Useful to create DOM elements not included in React.DOM. Equivalent
            to calling `js/React.createElement`"
           ([tag]
            (create-element tag nil))
           ([tag opts]
            (js/React.createElement tag opts))
           ([tag opts & children]
            (js/React.createElement tag opts children))))

#?(:cljs (def ^{:private true} element-marker
           (-> (js/React.createElement "div" nil)
             (gobj/get "$$typeof"))))

#?(:cljs (defn element? [x]
           (and (object? x)
             (= element-marker (gobj/get x "$$typeof")))))

#?(:cljs (defn convert-props [props]
           (cond
             (nil? props)
             #js {}
             (map? props)
             (clj->js props)
             :else
             props)))

;; called from macro
;; react v16 is really picky, the old direct .children prop trick no longer works
#?(:cljs (defn macro-create-element* [arr]
           {:pre [(array? arr)]}
           (.apply js/React.createElement nil arr)))

#?(:cljs (defn arr-append* [arr x]
           (.push arr x)
           arr))

#?(:cljs (defn arr-append [arr tail]
           (reduce arr-append* arr tail)))

;; fallback if the macro didn't do this
#?(:cljs (defn macro-create-element
           ([type args] (macro-create-element type args nil))
           ([type args csskw]
            (let [[head & tail] args]
              (cond
                (nil? head)
                (macro-create-element*
                  (doto #js [type (combine #js {} csskw)]
                    (arr-append tail)))

                (object? head)
                (macro-create-element*
                  (doto #js [type (combine head csskw)]
                    (arr-append tail)))

                (map? head)
                (macro-create-element*
                  (doto #js [type (clj->js (combine head csskw))]
                    (arr-append tail)))

                (element? head)
                (macro-create-element*
                  (doto #js [type (combine #js {} csskw)]
                    (arr-append args)))

                :else
                (macro-create-element*
                  (doto #js [type (combine #js {} csskw)]
                    (arr-append args))))))))
