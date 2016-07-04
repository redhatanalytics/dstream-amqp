/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.streaming.amqp

import org.apache.qpid.proton.amqp.messaging.{AmqpValue, Section}
import org.apache.qpid.proton.message.Message
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{Duration, Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkFunSuite}
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

/**
 * Scala test suite for the AMQP input stream
 */
class AMQPStreamSuite extends SparkFunSuite with Eventually with BeforeAndAfter {
  
  private val batchDuration: Duration = Seconds(1)
  private val master: String = "local[2]"
  private val appName: String = this.getClass().getSimpleName()
  private val address: String = "my_address"
  
  private var conf: SparkConf = _
  private var ssc: StreamingContext = _
  private var amqpTestUtils: AMQPTestUtils = _

  before {
    
    conf = new SparkConf().setMaster(master).setAppName(appName)
    ssc = new StreamingContext(conf, batchDuration)
    
    amqpTestUtils = new AMQPTestUtils()
    amqpTestUtils.setup()
  }
  
  after {
    
    if (ssc != null) {
      ssc.stop()
    }

    if (amqpTestUtils != null) {
      amqpTestUtils.teardown()
    }
  }

  test("AMQP receive simple body string") {

    amqpTestUtils.startBroker()

    val converter = new AMQPBodyFunction[String]

    val sendMessage = "Spark Streaming & AMQP"
    val receiveStream = AMQPUtils.createStream(ssc, amqpTestUtils.host, amqpTestUtils.port, address, converter, StorageLevel.MEMORY_ONLY)
    
    var receivedMessage: List[String] = List()
    receiveStream.foreachRDD(rdd => {
      if (!rdd.isEmpty()) {
        receivedMessage = receivedMessage ::: List(rdd.first())
      }
    })
    
    ssc.start()
    
    eventually(timeout(10000 milliseconds), interval(1000 milliseconds)) {
      amqpTestUtils.sendSimpleMessage(address, sendMessage)
      assert(sendMessage.equals(receivedMessage(0)))
    }
    ssc.stop()

    amqpTestUtils.stopBroker()
  }

  test("AMQP receive server") {

    val sendMessage = "Spark Streaming & AMQP"
    val max = 10

    amqpTestUtils.startAMQPServer(sendMessage, max)

    val converter = new AMQPBodyFunction[String]

    val receiveStream = AMQPUtils.createStream(ssc, amqpTestUtils.host, amqpTestUtils.port, address, converter, StorageLevel.MEMORY_ONLY)

    var receivedMessage: List[String] = List()
    receiveStream.foreachRDD(rdd => {
      if (!rdd.isEmpty()) {
        receivedMessage = receivedMessage ::: rdd.collect().toList
      }
    })

    ssc.start()

    eventually(timeout(10000 milliseconds), interval(1000 milliseconds)) {

      assert(receivedMessage.length == max)
    }
    ssc.stop()

    amqpTestUtils.stopAMQPServer()
  }

}