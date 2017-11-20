# clj-bom



## What

A tiny (100 LOC) Clojure library designed to abstract away the issue of dealing with BOMs (Byte-Order-Marks) in text-based files. The following BOMs are supported:

* UTF-8
* UTF-16LE
* UTF-16BE
* UTF-32LE
* UTF-32BE 

See [Wikipedia entry](https://en.wikipedia.org/wiki/Byte_order_mark)

## Where 

[![Clojars Project](https://img.shields.io/clojars/v/clj-bom.svg)](https://clojars.org/clj-bom)

## Why 

The purpose of BOMs is to signal the encoding of the characters to follow in the stream. For instance when you create a .csv file in Excel it will save it in UTF-8 (by default) adding three bytes (the UTF-8 BOM) at the start. Opening that same file with Excel is no problem because Excel is able to detect BOMs. If you, however, try to read that csv programmatically, you will find that the first word of the file is read with an extra leading (non-printable) character. Depending on what you actually do with the csv, this may not be a problem. But it could also be a major problem. Say for example that you use `clojure.data.csv` to read that file into a list of maps. Each map contains [column-name value] entries. In the presence of a BOM, there will always be one column (the first one as they were read in) which you won't be able to lookup with its printable name (what you see on the screen). So the column might be called `foo`, but you won't be able to do `(get % "foo")`. That's obviously a problem, and it can lead to spurious bugs. In fact, it appears that several Clojure users have tripped up over this in the past (see below).

1. [clojure.data.xml](https://dev.clojure.org/jira/browse/DXML-45)
2. [clojure.data.csv](https://dev.clojure.org/jira/browse/DCSV-7)
3. [StackOverflow](https://stackoverflow.com/questions/13789092/length-of-the-first-line-in-an-utf-8-file-with-bom)


## How

The API consists mainly of two functions. These are `bom-reader` and `bom-writer`. A couple of more predicates are available (e.g. `has-bom?` variants) but I'm not really convinced of their utility, at this point in time. 

### bom-reader

Takes a source (anything compatible with `io/input-stream`) which presumably starts with a BOM, and returns a Reader with the correct encoding, and the first character (the BOM) skipped (optionally). In the absence of a BOM, this is equivalent to calling `(io/reader source)`.

```clj
(require '[clj-bom :as bom] 
         '[clojure.data.csv :as csv])

;; instead of `io/reader`, use `bom/bom-reader`
(with-open [reader (bom/bom-reader "in-file-with-BOM.csv")]
  (doall (csv/read-csv reader)))
```


### bom-writer
 
Takes a target (anything compatible with `io/output-stream`) and returns a Writer (with the specified encoding) which will write the appropriate BOM before anything else.

```clj
(require '[clj-bom :as bom]
         '[clojure.data.csv :as csv])

;; instead of `io/writer`, use `bom/bom-writer`
(with-open [writer (bom/bom-writer "UTF-16LE" "out-file-with-BOM.csv")]
  (csv/write-csv writer
                 [["abc" "def"]
                 ["ghi" "jkl"]]))
```

`bom-input-stream` and `bom-output-stream` behave similarly with `bom-reader` and `bom-writer respectively`. The only difference is that they don't wrap the in/out streams in reader/writer objects.


## Alternatives 
If you already have `apache.commons.io` in your stack, then I guess `BOMInputStream` is the closest thing to what clj-bom tries to do. It is slightly lower level though. You would need to construct a BOMInputStream, manually detect the encoding, which you use in a subsequent `io/reader` call. 

## License

Copyright © 2017 Dimitrios Piliouras

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
