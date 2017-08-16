/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved
 *
 * This software is a modification of the original software Apache Spark licensed under the Apache 2.0
 * license, a copy of which is below. This software contains proprietary information of
 * Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or
 * otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled,
 * without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package org.apache.spark.examples.pythonconverters

import java.util.{Collection => JCollection, Map => JMap}

import scala.collection.JavaConverters._

import org.apache.avro.generic.{GenericFixed, IndexedRecord}
import org.apache.avro.mapred.AvroWrapper
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._

import org.apache.spark.api.python.Converter
import org.apache.spark.SparkException


object AvroConversionUtil extends Serializable {
  def fromAvro(obj: Any, schema: Schema): Any = {
    if (obj == null) {
      return null
    }
    schema.getType match {
      case UNION => unpackUnion(obj, schema)
      case ARRAY => unpackArray(obj, schema)
      case FIXED => unpackFixed(obj, schema)
      case MAP => unpackMap(obj, schema)
      case BYTES => unpackBytes(obj)
      case RECORD => unpackRecord(obj)
      case STRING => obj.toString
      case ENUM => obj.toString
      case NULL => obj
      case BOOLEAN => obj
      case DOUBLE => obj
      case FLOAT => obj
      case INT => obj
      case LONG => obj
      case other => throw new SparkException(s"Unknown Avro schema type ${other.getName}")
    }
  }

  def unpackRecord(obj: Any): JMap[String, Any] = {
    val map = new java.util.HashMap[String, Any]
    obj match {
      case record: IndexedRecord =>
        record.getSchema.getFields.asScala.zipWithIndex.foreach { case (f, i) =>
          map.put(f.name, fromAvro(record.get(i), f.schema))
        }
      case other => throw new SparkException(
        s"Unsupported RECORD type ${other.getClass.getName}")
    }
    map
  }

  def unpackMap(obj: Any, schema: Schema): JMap[String, Any] = {
    obj.asInstanceOf[JMap[_, _]].asScala.map { case (key, value) =>
      (key.toString, fromAvro(value, schema.getValueType))
    }.asJava
  }

  def unpackFixed(obj: Any, schema: Schema): Array[Byte] = {
    unpackBytes(obj.asInstanceOf[GenericFixed].bytes())
  }

  def unpackBytes(obj: Any): Array[Byte] = {
    val bytes: Array[Byte] = obj match {
      case buf: java.nio.ByteBuffer =>
        val arr = new Array[Byte](buf.remaining())
        buf.get(arr)
        arr
      case arr: Array[Byte] => arr
      case other => throw new SparkException(
        s"Unknown BYTES type ${other.getClass.getName}")
    }
    val bytearray = new Array[Byte](bytes.length)
    System.arraycopy(bytes, 0, bytearray, 0, bytes.length)
    bytearray
  }

  def unpackArray(obj: Any, schema: Schema): JCollection[Any] = obj match {
    case c: JCollection[_] =>
      c.asScala.map(fromAvro(_, schema.getElementType)).toSeq.asJava
    case arr: Array[_] if arr.getClass.getComponentType.isPrimitive =>
      arr.toSeq.asJava.asInstanceOf[JCollection[Any]]
    case arr: Array[_] =>
      arr.map(fromAvro(_, schema.getElementType)).toSeq.asJava
    case other => throw new SparkException(
      s"Unknown ARRAY type ${other.getClass.getName}")
  }

  def unpackUnion(obj: Any, schema: Schema): Any = {
    schema.getTypes.asScala.toList match {
      case List(s) => fromAvro(obj, s)
      case List(n, s) if n.getType == NULL => fromAvro(obj, s)
      case List(s, n) if n.getType == NULL => fromAvro(obj, s)
      case _ => throw new SparkException(
        "Unions may only consist of a concrete type and null")
    }
  }
}

/**
 * Implementation of [[org.apache.spark.api.python.Converter]] that converts
 * an Avro IndexedRecord (e.g., derived from AvroParquetInputFormat) to a Java Map.
 */
class IndexedRecordToJavaConverter extends Converter[IndexedRecord, JMap[String, Any]]{
  override def convert(record: IndexedRecord): JMap[String, Any] = {
    if (record == null) {
      return null
    }
    val map = new java.util.HashMap[String, Any]
    AvroConversionUtil.unpackRecord(record)
  }
}

/**
 * Implementation of [[org.apache.spark.api.python.Converter]] that converts
 * an Avro Record wrapped in an AvroKey (or AvroValue) to a Java Map. It tries
 * to work with all 3 Avro data mappings (Generic, Specific and Reflect).
 */
class AvroWrapperToJavaConverter extends Converter[Any, Any] {
  override def convert(obj: Any): Any = {
    if (obj == null) {
      return null
    }
    obj.asInstanceOf[AvroWrapper[_]].datum() match {
      case null => null
      case record: IndexedRecord => AvroConversionUtil.unpackRecord(record)
      case other => throw new SparkException(
        s"Unsupported top-level Avro data type ${other.getClass.getName}")
    }
  }
}
