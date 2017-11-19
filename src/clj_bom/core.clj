(ns clj-bom.core
  (:require [clojure.java.io :as io])
  (:import (java.util Arrays)
           (java.io InputStream Reader Writer)))

(defn- bom-bytes
  [unsigned-ints]
  (->> unsigned-ints
       (map int)
       byte-array)) ;; let the JVM cast the (unsigned) ints to (signed) bytes

;================<Common BOMs>================
;; Different encodings have different BOMs.
;; https://en.wikipedia.org/wiki/Byte_order_mark#Byte_order_marks_by_encoding

(defonce ^:private BOMs
  (let [charsets   ["UTF-8" "UTF-16LE" "UTF-16BE" "UTF-32LE"    "UTF-32BE"]
        boms [[239 187 191] [255 254]  [254 255]  [255 254 0 0] [0 0 254 255]]]
    (zipmap charsets
            (map bom-bytes boms))))

(defn- has-bom?
  "Given a BOM (Byte-Order-Mark) byte-array <bom>,
   and a source <in> (anything compatible with `io/input-stream`),
   returns true if the first N (`bom.length`) bytes of <in>
   are equal to <bom> - false otherwise."
  [^bytes bom-bytes in]
  (with-open [in (io/input-stream in)]
    (let [n-bytes (alength bom-bytes)
          first-n-bytes (byte-array n-bytes)
          n-bytes-read (.read in first-n-bytes)]
      (and (= n-bytes n-bytes-read)
           (Arrays/equals bom-bytes first-n-bytes)))))

(def has-utf8-bom?
  "Returns true if <in> starts with the UTF-8 BOM."
  (partial has-bom? (get BOMs "UTF-8")))

(def has-utf16be-bom?
  "Returns true if <in> starts with the UTF-16BE BOM."
  (partial has-bom? (get BOMs "UTF-16BE")))

(def has-utf16le-bom?
  "Returns true if <in> starts with the UTF-16LE BOM."
  (partial has-bom? (get BOMs "UTF-16LE")))

(def has-utf32le-bom?
  "Returns true if <in> starts with the UTF-32LE BOM."
  (partial has-bom? (get BOMs "UTF-32LE")))

(def has-utf32be-bom?
  "Returns true if <in> starts with the UTF-32BE BOM."
  (partial has-bom? (get BOMs "UTF-32BE")))

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
      (let [utf8-BOM (get BOMs "UTF-8")]
        (when (= (seq utf8-BOM)
                 (take 3 first-four-bytes))
          "UTF-8"))
      ;; UTF-32
      ;; check for UTF-32 before UTF-16 because UTF-16LE can be confused with UTF-32LE
      (let [utf32-le-BOM (get BOMs "UTF-32LE")
            utf32-be-BOM (get BOMs "UTF-32BE")]
        (condp = (seq first-four-bytes)
          (seq utf32-le-BOM) "UTF-32LE"
          (seq utf32-be-BOM) "UTF-32BE"
          nil))
      ;; UTF-16
      (let [utf16-le-BOM (get BOMs "UTF-16LE")
            utf16-be-BOM (get BOMs "UTF-16BE")]
        (condp = (take 2 first-four-bytes)
          (seq utf16-le-BOM) "UTF-16LE"
          (seq utf16-be-BOM) "UTF-16BE"
          nil)))))

(defn bom-reader
  "Given a source <in> (anything compatible with `io/input-stream`),
   returns a Reader wrapping it. If <in> starts with a BOM,
   the returned reader will have the correct encoding,
   while (optionally) skipping the first byte (depending on <skip-bom?>).
   In the absence of a BOM, this boils down to `(io/reader in)`.
   Must be called within a `with-open` expression to ensure that the
   returned Reader is closed appropriately."
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
   and it will add the BOM bytes specified by <the-bom> before anything else.
   Must be called within a `with-open` expression to ensure that the
   returned Writer is closed appropriately"
  ^Writer [charset out]
  (if-let [^bytes bom-bytes (get BOMs charset)]
    (let [ous (io/output-stream out)]
      (.write ous bom-bytes)
      (io/writer ous :encoding charset))
    (throw
      (IllegalArgumentException.
        (format "Charset [%s] is NOT recognised!" charset)))))


(comment
  ;; consider the case where you want to read a CSV file which has a BOM (e.g. produced by Excel).
  ;; all you need to do is to use `bom-reader` (as opposed to `io/reader`) which will give you back the reader with
  ;; the correct encoding, and (optionally) without the first character.
  (with-open [reader (bom-reader "in-file-with-BOM.csv")]
    (doall
      (csv/read-csv reader)))

  ;; and the opposite - produce a CSV with a BOM (e.g. so that Excel can see the encoding and open it correctly)
  (with-open [writer (bom-writer "UTF-8" "out-file-with-BOM.csv")]
    (csv/write-csv writer
                   [["abc" "def"]
                    ["ghi" "jkl"]]))

  )