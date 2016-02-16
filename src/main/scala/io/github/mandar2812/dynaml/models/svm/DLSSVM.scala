package io.github.mandar2812.dynaml.models.svm

import breeze.linalg.{DenseMatrix, DenseVector}
import io.github.mandar2812.dynaml.kernels.LocalSVMKernel
import io.github.mandar2812.dynaml.models.LinearModel
import io.github.mandar2812.dynaml.optimization.{LSSVMLinearSolver, RegularizedOptimizer}

/**
  * Created by mandar on 10/2/16.
  */
class DLSSVM(data: Stream[(DenseVector[Double], Double)], numPoints: Int,
             kern: LocalSVMKernel[DenseVector[Double]])
  extends LinearModel[Stream[(DenseVector[Double], Double)],
    Int, Int, DenseVector[Double], DenseVector[Double], Double,
    (DenseMatrix[Double], DenseVector[Double])] {

  override protected val g = data

  val kernel = kern

  val num_points = numPoints

  override def initParams(): DenseVector[Double] =
    DenseVector.ones[Double](num_points+1)

  override def clearParameters(): Unit = {
    params = initParams()
  }

  /**
    * Learn the parameters
    * of the model which
    * are in a node of the
    * graph.
    *
    **/
  override def learn(): Unit = {
    params = optimizer.optimize(num_points,
      (kernel.buildKernelMatrix(g.map(_._1).toSeq, num_points).getKernelMatrix(),
        DenseVector(g.map(_._2).toArray)),
      initParams())
  }

  override protected val optimizer: RegularizedOptimizer[Int,
    DenseVector[Double], DenseVector[Double], Double,
    (DenseMatrix[Double], DenseVector[Double])] = new LSSVMLinearSolver()

  override protected var params: DenseVector[Double] = initParams()

  /**
    * Predict the value of the
    * target variable given a
    * point.
    *
    **/
  override def predict(point: DenseVector[Double]): Double = {

    val features = DenseVector(g.map(inducingpoint =>
      kernel.evaluate(point, inducingpoint._1)).toArray)

    params(0 to num_points-1) dot features + params(-1)
  }

}