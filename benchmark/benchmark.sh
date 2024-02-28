#!/bin/bash

JAR="QAlloy-F.jar"
N=10
T=600000

OPTSTRING=":n:t:j:h"

while getopts ${OPTSTRING} opt; do
  case ${opt} in
    n)
      N=${OPTARG}
      ;;
    t)
      T=${OPTARG}
      ;;
    j)
      JAR=${OPTARG}
      ;;
    h)
      echo "================================================================
 QAlloy-F Benchmark
================================================================
% SYNOPSIS
+    benchmark [-n[number]] [-t[timeout]] [-j[JAR]] alsFiles ..
%
% DESCRIPTION
%    Benchmark QAlloy-F against the specified models.
%
% OPTIONS
%    -n [number]                   Number of runs per command
%    -t [timeout]                  Set timeout for each run
%    -j [JAR]                      Set QAlloy-F JAR name
%    -h                            Print this help
%
% EXAMPLE
%    benchmark -n 10 -t 60000 heater.als"
      exit 1
      ;;
    :)
      echo "Option -${OPTARG} requires an argument."
      exit 1
      ;;
    ?)
      echo "Invalid option: -${OPTARG}."
      exit 1
      ;;
  esac
done

#echo ${N} 
#echo ${T} 
#echo ${JAR}
shift $(($OPTIND - 1))
#echo "$@"

java -cp ${JAR} edu.mit.csail.sdg.alloy4whole.Benchmark ${N} ${T} "$@"
mkdir -p results
mv times/* results
rm -r times/
