(ns clj-bom.core
  (:require [clojure.java.io :as io])
  (:import (java.util Arrays)
           (java.io InputStream Reader Writer)))

(defmacro ^:private defBOM
  [sym doc-str unsigned-ints]
  `(def ~sym ~doc-str
     {:charset ~doc-str ;; the doc-string specifies the actual charset
      :bytes (->> ~unsigned-ints
                  (map int)
                  byte-array)})) ;; let the JVM cast the (unsigned) ints to (signed) bytes,

;================<Common BOMs>================
;; Different encodings have different BOMs.
;; https://en.wikipedia.org/wiki/Byte_order_mark#Byte_order_marks_by_encoding

(defBOM utf8-BOM
        "UTF-8"
        [239 187 191])

(defBOM utf16-le-BOM
        "UTF-16LE"
        [255 254]) ;;

(defBOM utf16-be-BOM
        "UTF-16BE"
        [254 255])

(defBOM utf32-be-BOM
        "UTF-32BE"
        [0 0 254 255])

(defBOM utf32-le-BOM
        "UTF-32LE"
        [255 254 0 0])

(defn- has-bom?
  "Given a BOM (Byte-Order-Mark) byte-array <bom>,
   and a source <in> (anything compatible with `io/input-stream`),
   returns true if the first N (`bom.length`) bytes of <in>
   are equal to <bom> - false otherwise."
  [the-bom in]
  (let [^bytes bom (:bytes the-bom)]
    (with-open [in (io/input-stream in)]
      (let [n-bytes (alength bom)
            first-n-bytes (byte-array n-bytes)
            n-bytes-read (.read in first-n-bytes)]
        (and (= n-bytes n-bytes-read)
             (Arrays/equals bom first-n-bytes))))))

(def has-utf8-bom?
  "Returns true if <in> starts with the UTF-8 BOM."
  (partial has-bom? utf8-BOM))

(def has-utf16be-bom?
  "Returns true if <in> starts with the UTF-16BE BOM."
  (partial has-bom? utf16-be-BOM))

(def has-utf16le-bom?
  "Returns true if <in> starts with the UTF-16LE BOM."
  (partial has-bom? utf16-le-BOM))

(def has-utf32le-bom?
  "Returns true if <in> starts with the UTF-32LE BOM."
  (partial has-bom? utf32-le-BOM))

(def has-utf32be-bom?
  "Returns true if <in> starts with the UTF-32BE BOM."
  (partial has-bom? utf32-be-BOM))

(defn detect-encoding
  "Given an InputStream <in>, attempts to detect the
   character encoding by looking at the first 4 bytes.
   Returns a character encoding (UTF-8, UTF-16LE, UTF-16BE, UTF-32LE, UTF-32BE),
   or nil if no BOM is present."
  [^InputStream in]
  (.mark in 4) ;; mark the start of the stream (allowing 4 bytes to be read before invalidating the mark-position)
  (let [first-four-bytes (byte-array 4)]
    (.read in first-four-bytes) ;; read the first 4 bytes
    (.reset in)                 ;; and reset to the starting position immediately
    (or
      ;; UTF-8
      ;; check UTF-8 first as it's the most common
      (when (= (seq (:bytes utf8-BOM))
               (take 3 first-four-bytes))
        (:charset utf8-BOM))
      ;; UTF-32
      ;; check for UTF-32 before UTF-16 because UTF-16LE can be confused with UTF-32LE
      (condp = (seq first-four-bytes)
        (-> utf32-le-BOM :bytes seq) (:charset utf32-le-BOM)
        (-> utf32-be-BOM :bytes seq) (:charset utf32-be-BOM)
        nil)
      ;; UTF-16
      (condp = (take 2 first-four-bytes)
        (-> utf16-le-BOM :bytes seq) (:charset utf16-le-BOM)
        (-> utf16-be-BOM :bytes seq) (:charset utf16-be-BOM)
        nil))))

(defn bom-reader
  "Given a source <in> (anything compatible with `io/input-stream`),
   returns a Reader wrapping it. If <in> starts with a BOM,
   the returned reader will have the correct encoding,
   while (optionally) skipping the first byte (depending on <skip-bom?>).
   In the absence of a BOM, this boils down to `(io/reader in)`.
   Must be called within a `with-open` expression to ensure that the
   returned Reader (which wraps <in>) is closed appropriately."
  (^Reader [in]
   (bom-reader in true))
  (^Reader [in skip-bom?]
   (let [is (io/input-stream in)]
     (if-let [encoding (detect-encoding is)] ;; check to see if any Unicode BOMs match
       (cond-> (io/reader is :encoding encoding)
               skip-bom? (doto (.skip 1)))  ;; skip the first character (the BOM) from the returned Reader
       (io/reader is)))))                   ;; do nothing - return a Reader with the default encoding

(defn bom-writer
  "Given a target <out> (anything compatible with `io/output-stream`),
   returns a Writer wrapping it. The returned writer will have the correct encoding,
   and it will add the BOM bytes specified by <the-bom> before anything else."
  ^Writer [{:keys [^bytes bytes charset] :as the-bom}  out]
  (let [ous (io/output-stream out)]
    (.write ous bytes)
    (io/writer ous :encoding charset)))


(comment
  ;; consider the case where you want to read a CSV file which has a BOM (e.g. produced by Excel).
  ;; all you need to do is first create an input-stream and pass that to 
  ;; `bom-input-stream->reader` which will give you back the reader with 
  ;; the correct encoding and without the first byte.
  (with-open [reader (bom-reader "in-file-with-BOM.csv")]
    (doall
      (csv/read-csv reader)))

  ;; and the opposite - produce a CSV with a BOM (e.g. so that Excel can see the encoding and open it correctly)
  (with-open [writer (bom-writer utf8-BOM "out-file-with-BOM.csv")]
    (csv/write-csv writer
                   [["abc" "def"]
                    ["ghi" "jkl"]]))

  )