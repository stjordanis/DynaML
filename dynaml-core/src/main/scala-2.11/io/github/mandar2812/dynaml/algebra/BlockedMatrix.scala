package io.github.mandar2812.dynaml.algebra

import breeze.linalg.{DenseMatrix, NumericOps}
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
  * @author mandar2812 date 13/10/2016.
  * A distributed matrix that is stored in blocks.
  *
  * @param data The underlying [[RDD]] which should consist of
  *             block indices and a breeze [[DenseMatrix]] containing
  *             all the elements in the said block.
  */
private[dynaml] class BlockedMatrix(data: RDD[((Long, Long), DenseMatrix[Double])],
                                     num_rows: Long = -1L, num_cols: Long = -1L,
                                     num_row_blocks: Long = -1L, num_col_blocks: Long = -1L)
  extends NumericOps[BlockedMatrix] {

  lazy val rowBlocks = if(num_row_blocks == -1L) data.keys.map(_._1).max else num_row_blocks

  lazy val colBlocks = if(num_col_blocks == -1L) data.keys.map(_._2).max else num_col_blocks


  lazy val rows: Long = if(num_rows == -1L) data.filter(_._1._2 == 1).map(_._2.rows).sum().toLong else num_rows

  lazy val cols: Long = if(num_cols == -1L) data.filter(_._1._1 == 1).map(_._2.cols).sum().toLong else num_cols

  def _data = data


  override def repr: BlockedMatrix = this

  /**
    * Transpose of blocked matrix
    * */
  def t: BlockedMatrix = new BlockedMatrix(
    data.map(c => (c._1.swap, c._2.t)),
    cols, rows, colBlocks, rowBlocks)

  def persist: Unit = {
    data.persist(StorageLevel.MEMORY_AND_DISK)
  }

  def unpersist: Unit = {
    data.unpersist()
  }

}

object BlockedMatrix {

  /**
    * Create a [[BlockedMatrix]] from a [[SparkMatrix]], this
    * method takes the underlying key-value [[RDD]] and groups it
    * by blocks converting each block to a breeze [[DenseMatrix]]
    *
    * @tparam T The type of the [[SparkMatrix]] instance
    *
    * @param m The distributed matrix
    * @param numElementsRowBlock Maximum number of rows in each block
    * @param numElementsColBlock Maximum number of columns in each block matrix
    *
    */
  def apply[T <: SparkMatrix](m: T, numElementsRowBlock: Int, numElementsColBlock: Int): BlockedMatrix = {
    new BlockedMatrix(
      m._matrix
        .map(e => ((e._1._1/numElementsRowBlock, e._1._2/numElementsColBlock),e))
        .groupByKey().map(c => {

        //Construct local block matrices
        val (blocIndex, locData) = (
          c._1,
          c._2.map(el =>
            ((
              el._1._1 - c._1._1*numElementsRowBlock,
              el._1._2 - c._1._2*numElementsColBlock),el._2)).toMap
          )

        (blocIndex, DenseMatrix.tabulate[Double](
          locData.keys.map(_._1).max.toInt + 1,
          locData.keys.map(_._2).max.toInt + 1)((i,j) => locData(i,j)))
      }),
      num_rows = m.rows, num_cols = m.cols,
      num_row_blocks = m.rows/numElementsRowBlock,
      num_col_blocks = m.cols/numElementsColBlock
    )
  }
}
