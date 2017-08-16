#
# © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved
#
# This software is a modification of the original software Apache Spark licensed under the Apache 2.0
# license, a copy of which is below. This software contains proprietary information of
# Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or
# otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled,
# without express written authorization from Stratio Big Data Inc., Sucursal en España.
#

"""
Streaming Linear Regression Example.
"""
from __future__ import print_function

# $example on$
import sys
# $example off$

from pyspark import SparkContext
from pyspark.streaming import StreamingContext
# $example on$
from pyspark.mllib.linalg import Vectors
from pyspark.mllib.regression import LabeledPoint
from pyspark.mllib.regression import StreamingLinearRegressionWithSGD
# $example off$

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: streaming_linear_regression_example.py <trainingDir> <testDir>",
              file=sys.stderr)
        exit(-1)

    sc = SparkContext(appName="PythonLogisticRegressionWithLBFGSExample")
    ssc = StreamingContext(sc, 1)

    # $example on$
    def parse(lp):
        label = float(lp[lp.find('(') + 1: lp.find(',')])
        vec = Vectors.dense(lp[lp.find('[') + 1: lp.find(']')].split(','))
        return LabeledPoint(label, vec)

    trainingData = ssc.textFileStream(sys.argv[1]).map(parse).cache()
    testData = ssc.textFileStream(sys.argv[2]).map(parse)

    numFeatures = 3
    model = StreamingLinearRegressionWithSGD()
    model.setInitialWeights([0.0, 0.0, 0.0])

    model.trainOn(trainingData)
    print(model.predictOnValues(testData.map(lambda lp: (lp.label, lp.features))))

    ssc.start()
    ssc.awaitTermination()
    # $example off$
