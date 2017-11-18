(ns clj-bom.core
  (:require [clojure.java.io :as io])
  (:import (java.util Arrays)
           (java.io InputStream BufferedReader BufferedOutputStream OutputStream)))

(defmacro ^:private defBOM
  [sym doc-str unsigned-ints]
  `(def ~sym ~doc-str
     {:charset ~doc-str
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
        [255 254])

(defBOM utf16-be-BOM
        "UTF-16BE"
        [254 255])

(defBOM utf32-be-BOM
        "UTF-32BE"
        [0 0 254 255])

(defBOM utf32-le-BOM
        "UTF-32LE"
        [255 254 0 0])

(defn has-bom?
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

(defn detect-charset
  "Given an InputStream <in>, attempts to detect the
   character encoding by looking at the first 4 bytes.
   Returns a character encoding (UTF-8, UTF-16LE, UTF-16BE, UTF-32LE, UTF-32BE),
   or nil if no BOM is present."
  [^InputStream in]
  (.mark in 4) ;; mark the start of the stream (allowing 4 bytes to be read before invalidating the mark-position)
  (let [first-n-bytes (byte-array 4)
        _n-bytes-read (.read in first-n-bytes)]
    (.reset in) ;; reset to the starting position
    (or
      ;; UTF-8
      (when (= (seq (:bytes utf8-BOM))
               (take 3 first-n-bytes))
        (:charset utf8-BOM))
      ;; UTF-16
      (let [two-bs (take 2 first-n-bytes)]
        (cond
          (= (seq (:bytes utf16-le-BOM))
             two-bs) (:charset utf16-le-BOM)
          (= (seq (:bytes utf16-be-BOM))
             two-bs) (:charset utf16-be-BOM)))
      ;; UTF-32
      (let [four-bs (seq first-n-bytes)]
        (cond
          (= (seq (:bytes utf32-le-BOM))
             four-bs) (:charset utf32-le-BOM)
          (= (seq (:bytes utf32-be-BOM))
             four-bs) (:charset utf32-be-BOM))))))

(defn bom-reader
  "Given an InputStream <in>, returns a Reader wrapping it.
   If <in> starts with a BOM, the returned reader will have the correct encoding,
   while skipping the first byte. In the absence of a BOM,
   this boils down to `(io/reader in)`. Must be called within a
   `with-open` expression to ensure that the returned Reader (which wraps <in>)
   is closed appropriately."
  ^BufferedReader [^InputStream in]
  (if-let [encoding (detect-charset in)] ;; check to see if any Unicode BOMs match
    (doto (io/reader in :encoding encoding)
      (.skip 1))      ;; skip 1 character (the BOM) from the returned Reader
    (io/reader in))) ;; do nothing - return a Reader with the default encoding

(defn bom-writer
  "Given an OutputStream <out>, returns a Writer wrapping it.
   The returned writer will have the correct encoding,
   and it will add the BOM specified by <bom-var> before anything else."
  [{:keys [bytes charset] :as the-bom} ^OutputStream out]
  (.write out ^bytes bytes)
  (io/writer out :encoding charset))


(comment
  ;; consider the case where you want to read a CSV file which has a BOM (e.g. produced by Excel).
  ;; all you need to do is first create an input-stream and pass that to 
  ;; `bom-input-stream->reader` which will give you back the reader with 
  ;; the correct encoding and without the first byte.
  (with-open [reader (bom-reader (io/input-stream "in-file-with-BOM.csv"))]
    (doall
      (csv/read-csv reader)))

  ;; and the opposite - produce a CSV with a BOM (e.g. so that Excel can see the encoding and open it correctly)
  (with-open [writer (bom-writer utf8-BOM (io/output-stream "out-file-with-BOM.csv"))]
    (csv/write-csv writer
                   [["abc" "def"]
                    ["ghi" "jkl"]]))

  )