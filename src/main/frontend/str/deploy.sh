#!/bin/sh
s3cmd -P --exclude "data/*" sync . s3://str.appening.at/               

