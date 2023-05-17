/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.buffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.apache.dubbo.remoting.buffer.ChannelBuffers.directBuffer;
import static org.apache.dubbo.remoting.buffer.ChannelBuffers.wrappedBuffer;
import static org.junit.jupiter.api.Assertions.*;


public abstract class AbstractChannelBufferTest {

    private static final int CAPACITY = 4096; // Must be even
    private static final int BLOCK_SIZE = 128;

    private long seed; //seed：随机数的种子
    private Random random;
    private ChannelBuffer buffer;

    protected abstract ChannelBuffer newBuffer(int capacity);

    protected abstract ChannelBuffer[] components();

    protected boolean discardReadBytesDoesNotMoveWritableBytes() {
        return true;
    }


    @BeforeEach
    public void init() {
        buffer = newBuffer(CAPACITY); //父类调用子类实现的抽象方法，获取到结果（面向抽象编程）
        seed = System.currentTimeMillis();
        random = new Random(seed);
    }

    @AfterEach
    public void dispose() {
        buffer = null;
    }

    @Test
    public void initialState() { //直接运行是运行不了，要指定具体的子类（抽象类不能直接实例化）
        assertEquals(CAPACITY, buffer.capacity()); //buffer设置了CAPACITY的容量
        assertEquals(0, buffer.readerIndex()); //初始时，读下标和写下标都是为0
    }

    @Test
    public void readerIndexBoundaryCheck1() { //读下标越界检查
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            try {
                buffer.writerIndex(0);
            } catch (IndexOutOfBoundsException e) {
                fail();
            }
            buffer.readerIndex(-1); //读下标不能小于0
        });
    }

    @Test
    public void readerIndexBoundaryCheck2() { //测试readerIndex下标是否越界
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            try {
                buffer.writerIndex(buffer.capacity());
            } catch (IndexOutOfBoundsException e) {
                fail();
            }
            buffer.readerIndex(buffer.capacity() + 1); //readerIndex 需要小于等于writerIndex
        });
    }

    @Test
    public void readerIndexBoundaryCheck3() { //同上测试下标
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            try {
                buffer.writerIndex(CAPACITY / 2);
            } catch (IndexOutOfBoundsException e) {
                fail();
            }
            buffer.readerIndex(CAPACITY * 3 / 2);
        });
    }

    @Test
    public void readerIndexBoundaryCheck4() { //读写下标值可以一致
        buffer.writerIndex(0);
        buffer.readerIndex(0);
        buffer.writerIndex(buffer.capacity());
        buffer.readerIndex(buffer.capacity());
    }

    @Test
    public void writerIndexBoundaryCheck1() { //读写下标都不能 <0
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            buffer.writerIndex(-1);
        });
    }

    @Test
    public void writerIndexBoundaryCheck2() { //测试写下标

        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            try {
                buffer.writerIndex(CAPACITY);
                buffer.readerIndex(CAPACITY);
            } catch (IndexOutOfBoundsException e) {
                fail();
            }
            buffer.writerIndex(buffer.capacity() + 1); //写下标不能超过capacity
        });
    }

    @Test
    public void writerIndexBoundaryCheck3() { //测试读下标越界的情况
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            try {
                buffer.writerIndex(CAPACITY);
                buffer.readerIndex(CAPACITY / 2);
            } catch (IndexOutOfBoundsException e) {
                fail();
            }
            buffer.writerIndex(CAPACITY / 4); //此处抛出异常是writerIndex小于了readerIndex
        });
    }

    @Test
    public void writerIndexBoundaryCheck4() { //正常使用情况
        buffer.writerIndex(0);
        buffer.readerIndex(0);
        buffer.writerIndex(CAPACITY);
    }

    @Test
    public void getByteBoundaryCheck1() { //数组下标越界场景
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.getByte(-1));
    }

    @Test
    public void getByteBoundaryCheck2() { // buffer数组的下标范围 0 <= x <= capacity
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.getByte(buffer.capacity()));
    }

    @Test
    public void getByteArrayBoundaryCheck1() { //从当前buffer拷贝内容到指定数组时，数据越界
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBytes(-1, new byte[0]));
    }

    @Test
    public void getByteArrayBoundaryCheck2() { //同上
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBytes(-1, new byte[0], 0, 0));
    }

    @Test
    public void getByteBufferBoundaryCheck() { //ByteBuffer是java的字节缓冲区
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBytes(-1, ByteBuffer.allocate(0)));
    }

    @Test
    public void copyBoundaryCheck1() { //buffer内容拷贝，但是下标越界了
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.copy(-1, 0));
    }

    @Test
    public void copyBoundaryCheck2() { //buffer内容拷贝，但是内容长度越界了
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.copy(0, buffer.capacity() + 1));
    }

    @Test
    public void copyBoundaryCheck3() { //同上类似测试
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.copy(buffer.capacity() + 1, 0));
    }

    @Test
    public void copyBoundaryCheck4() { //同上类似测试
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.copy(buffer.capacity(), 1));
    }

    @Test
    public void setIndexBoundaryCheck1() { //读下标越界
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setIndex(-1, CAPACITY));
    }

    @Test
    public void setIndexBoundaryCheck2() { //写下标小于读下标异常
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setIndex(CAPACITY / 2, CAPACITY / 4));
    }

    @Test
    public void setIndexBoundaryCheck3() { //写下标大于容量
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.setIndex(0, CAPACITY + 1));
    }

    @Test
    public void getByteBufferState() { //测试java API中的ByteBuffer
        ByteBuffer dst = ByteBuffer.allocate(4); //分配指定容量的缓存区，默认为HeapByteBuffer实现类
//        ByteBuffer dst = new ByteBuffer(); //ByteBuffer是抽象类，不能直接new对象
        dst.position(1);
        dst.limit(3);

        // java.nio.Buffer中的mark <= position <= limit <= capacity，这些标识是ByteBuffer继承Buffer的，ByteBuffer中维护中字节数组

        buffer.setByte(0, (byte) 1);
        buffer.setByte(1, (byte) 2);
        buffer.setByte(2, (byte) 3);
        buffer.setByte(3, (byte) 4);
        buffer.getBytes(1, dst); //根据HeapChannelBuffer中的实现逻辑，会从当前buffer中index=1，且拷贝dst的limit-position数量的元素，最终是拷贝了两个元素

        assertEquals(3, dst.position()); //因为增加了两个长度，所以position位置也对应增加2，即1+2=3（每写入一个元素，position就增加1）
        assertEquals(3, dst.limit()); //limit的长度保持不对

        dst.clear(); //清理游标，将position=0，limit=capacity，mark=-1（但并没清理缓冲区数据）
        assertEquals(0, dst.get(0));
        assertEquals(2, dst.get(1)); //值来源于上文中buffer.getBytes(1, dst)的拷贝
        assertEquals(3, dst.get(2));
        assertEquals(0, dst.get(3));
    }

    @Test
    public void getDirectByteBufferBoundaryCheck() { //此处会在java.nio.Buffer#checkBounds()中检查下标越界时抛出下标越界异常
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBytes(-1, ByteBuffer.allocateDirect(0)));
    }

    @Test
    public void getDirectByteBufferState() {
        ByteBuffer dst = ByteBuffer.allocateDirect(4); //分配指定容量的缓存区，对应的实现类为DirectByteBuffer
        dst.position(1);
        dst.limit(3);

        buffer.setByte(0, (byte) 1);
        buffer.setByte(1, (byte) 2);
        buffer.setByte(2, (byte) 3);
        buffer.setByte(3, (byte) 4);
        buffer.getBytes(1, dst);

        assertEquals(3, dst.position());
        assertEquals(3, dst.limit());

        dst.clear();
        assertEquals(0, dst.get(0));
        assertEquals(2, dst.get(1));
        assertEquals(3, dst.get(2));
        assertEquals(0, dst.get(3));
    }

    @Test
    public void testRandomByteAccess() { //测试随机数的访问
        for (int i = 0; i < buffer.capacity(); i++) {
            byte value = (byte) random.nextInt();
            buffer.setByte(i, value);
        }

        random.setSeed(seed); //此处若不设置，产生的随机值就不一致了（此处还待研究，有点神奇，为啥设置了 种子数，后续产生的值就一样了，随机数变为可预测的数了）
        for (int i = 0; i < buffer.capacity(); i++) {
            byte value = (byte) random.nextInt();
            assertEquals(value, buffer.getByte(i));
        }

        System.out.println("随机数：" + Math.random());

        /**
         * Random生成的随机数都是伪随机数！！！
         * 是由可确定的函数（常用线性同余），通过一个种子（常用时钟），产生的伪随机数。这意味着：如果知道了种子，或者已经产生的随机数，都可能获得接下来随机数序列的信息（可预测性）
         * 只有通过真实的随机事件产生的随机数才是真随机！！比如，通过机器的硬件噪声产生随机数、通过大气噪声产生随机数
         *
         * https://juejin.cn/post/6976248310692577294
         */
    }

    @Test
    public void testSequentialByteAccess() { //测试序列化读或写操作
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity(); i++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.writerIndex());
            assertTrue(buffer.writable());
            buffer.writeByte(value); //写操作执行后 writerIndex下标会自动+1
        }

        assertEquals(0, buffer.readerIndex()); //因为没有进行读操作，所以读下标未移动
        assertEquals(buffer.capacity(), buffer.writerIndex()); //writerIndex最多可以写到capacity
        assertFalse(buffer.writable()); //因为写满了，所以不能再写了

        random.setSeed(seed);
        for (int i = 0; i < buffer.capacity(); i++) {
            byte value = (byte) random.nextInt();
            assertEquals(i, buffer.readerIndex());
            assertTrue(buffer.readable());
            assertEquals(value, buffer.readByte()); //进行读操作以后，readerIndex会自动增加
        }

        assertEquals(buffer.capacity(), buffer.readerIndex()); //读、写操作，只对应影响readerIndex或writerIndex，不会同时影响
        assertEquals(buffer.capacity(), buffer.writerIndex());
        assertFalse(buffer.readable());
        assertFalse(buffer.writable());
    }

    @Test
    public void testByteArrayTransfer() { //测试字节数组转换
        byte[] value = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value); //将随机产生的字节数组，回填指定的字节数组

            /**
             * 此处处理的逻辑：
             * 1）随机数产生的范围 0 ~ BLOCK_SIZE
             * 2）数组拷贝下标是 0 ~ BLOCK_SIZE，长度为BLOCK_SIZE，而数组长度为BLOCK_SIZE*2，所以拷贝最大情况是BLOCK_SIZE ~ BLOCK_SIZE + BLOCK_SIZE，也不会出现数组越界
             * 3）将输入的字节数组进行拷贝，相关元素写到当前buffer中的字节数组中
             */
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE); //将指定数组的元素设置到当前缓冲区的数组中
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testRandomByteArrayTransfer1() { //测试随机数组转换
        byte[] value = new byte[BLOCK_SIZE];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            buffer.setBytes(i, value); //将产生随机数组成的字节数组，设置到当前buffer的字节数组（此处value对应的字节数组会被随机数替换）
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            buffer.getBytes(i, value); //获取当前buffer中的字节数组，并写道输入的目标数组中
            for (int j = 0; j < BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value[j]);
            }
        }
    }

    @Test
    public void testRandomByteArrayTransfer2() { //测试数组转换
        byte[] value = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE); //指定目标数组的处理下标
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value[j]);
            }
        }
    }

    @Test
    public void testRandomHeapBufferTransfer1() { //已阅读
        byte[] valueContent = new byte[BLOCK_SIZE];
        ChannelBuffer value = wrappedBuffer(valueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            value.setIndex(0, BLOCK_SIZE);
            buffer.setBytes(i, value);
            assertEquals(BLOCK_SIZE, value.readerIndex());
            assertEquals(BLOCK_SIZE, value.writerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            value.clear();
            buffer.getBytes(i, value);
            assertEquals(0, value.readerIndex());
            assertEquals(BLOCK_SIZE, value.writerIndex());
            for (int j = 0; j < BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
        }
    }

    @Test
    public void testRandomHeapBufferTransfer2() { //已阅读
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = wrappedBuffer(valueContent); //封装的buffer实例为HeapChannelBuffer
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
        }
    }

    @Test
    public void testRandomDirectBufferTransfer() { //已调试
        byte[] tmp = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = directBuffer(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(tmp);
            value.setBytes(0, tmp, 0, value.capacity());
            buffer.setBytes(i, value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
        }

        random.setSeed(seed);
        ChannelBuffer expectedValue = directBuffer(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(tmp);
            expectedValue.setBytes(0, tmp, 0, expectedValue.capacity());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            buffer.getBytes(i, value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
        }
    }

    @Test
    public void testRandomByteBufferTransfer() { //已阅读
        ByteBuffer value = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value.array());
            value.clear().position(random.nextInt(BLOCK_SIZE));
            value.limit(value.position() + BLOCK_SIZE);
            buffer.setBytes(i, value);
        }

        random.setSeed(seed);
        ByteBuffer expectedValue = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue.array());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.clear().position(valueOffset).limit(valueOffset + BLOCK_SIZE);
            buffer.getBytes(i, value);
            assertEquals(valueOffset + BLOCK_SIZE, value.position());
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.get(j), value.get(j));
            }
        }
    }

    @Test
    public void testSequentialByteArrayTransfer1() { //已阅读
        byte[] value = new byte[BLOCK_SIZE];
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value);
            for (int j = 0; j < BLOCK_SIZE; j++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testSequentialByteArrayTransfer2() { //已阅读
        byte[] value = new byte[BLOCK_SIZE * 2];
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            int readerIndex = random.nextInt(BLOCK_SIZE);
            buffer.writeBytes(value, readerIndex, BLOCK_SIZE);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE * 2];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue[j], value[j]);
            }
        }
    }

    @Test
    public void testSequentialHeapBufferTransfer1() { //已阅读
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = wrappedBuffer(valueContent);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
            assertEquals(0, value.readerIndex());
            assertEquals(valueContent.length, value.writerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(0, value.readerIndex());
            assertEquals(valueContent.length, value.writerIndex());
        }
    }

    @Test
    public void testSequentialHeapBufferTransfer2() {//已阅读
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = wrappedBuffer(valueContent);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            int readerIndex = random.nextInt(BLOCK_SIZE);
            value.readerIndex(readerIndex);
            value.writerIndex(readerIndex + BLOCK_SIZE);
            buffer.writeBytes(value);
            assertEquals(readerIndex + BLOCK_SIZE, value.writerIndex());
            assertEquals(value.writerIndex(), value.readerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            value.readerIndex(valueOffset);
            value.writerIndex(valueOffset);
            buffer.readBytes(value, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(valueOffset, value.readerIndex());
            assertEquals(valueOffset + BLOCK_SIZE, value.writerIndex());
        }
    }

    @Test
    public void testSequentialDirectBufferTransfer1() {//已阅读
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = directBuffer(BLOCK_SIZE * 2);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            value.setBytes(0, valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
            assertEquals(0, value.readerIndex());
            assertEquals(0, value.writerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.setBytes(0, valueContent);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(0, value.readerIndex());
            assertEquals(0, value.writerIndex());
        }
    }

    @Test
    public void testSequentialDirectBufferTransfer2() { //已阅读
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = directBuffer(BLOCK_SIZE * 2);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            value.setBytes(0, valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            int readerIndex = random.nextInt(BLOCK_SIZE);
            value.readerIndex(0);
            value.writerIndex(readerIndex + BLOCK_SIZE);
            value.readerIndex(readerIndex);
            buffer.writeBytes(value);
            assertEquals(readerIndex + BLOCK_SIZE, value.writerIndex());
            assertEquals(value.writerIndex(), value.readerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            value.setBytes(0, valueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            value.readerIndex(valueOffset);
            value.writerIndex(valueOffset);
            buffer.readBytes(value, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(valueOffset, value.readerIndex());
            assertEquals(valueOffset + BLOCK_SIZE, value.writerIndex());
        }
    }

    @Test
    public void testSequentialByteBufferBackedHeapBufferTransfer1() { //已阅读
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = wrappedBuffer(ByteBuffer.allocate(BLOCK_SIZE * 2));
        value.writerIndex(0);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            value.setBytes(0, valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value, random.nextInt(BLOCK_SIZE), BLOCK_SIZE);
            assertEquals(0, value.readerIndex());
            assertEquals(0, value.writerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.setBytes(0, valueContent);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            buffer.readBytes(value, valueOffset, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(0, value.readerIndex());
            assertEquals(0, value.writerIndex());
        }
    }

    @Test
    public void testSequentialByteBufferBackedHeapBufferTransfer2() { //已阅读
        byte[] valueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer value = wrappedBuffer(ByteBuffer.allocate(BLOCK_SIZE * 2));
        value.writerIndex(0);
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(valueContent);
            value.setBytes(0, valueContent);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            int readerIndex = random.nextInt(BLOCK_SIZE);
            value.readerIndex(0);
            value.writerIndex(readerIndex + BLOCK_SIZE);
            value.readerIndex(readerIndex);
            buffer.writeBytes(value);
            assertEquals(readerIndex + BLOCK_SIZE, value.writerIndex());
            assertEquals(value.writerIndex(), value.readerIndex());
        }

        random.setSeed(seed);
        byte[] expectedValueContent = new byte[BLOCK_SIZE * 2];
        ChannelBuffer expectedValue = wrappedBuffer(expectedValueContent);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValueContent);
            value.setBytes(0, valueContent);
            int valueOffset = random.nextInt(BLOCK_SIZE);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            value.readerIndex(valueOffset);
            value.writerIndex(valueOffset);
            buffer.readBytes(value, BLOCK_SIZE);
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.getByte(j), value.getByte(j));
            }
            assertEquals(valueOffset, value.readerIndex());
            assertEquals(valueOffset + BLOCK_SIZE, value.writerIndex());
        }
    }

    @Test
    public void testSequentialByteBufferTransfer() { //已阅读
        buffer.writerIndex(0);
        ByteBuffer value = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(value.array());
            value.clear().position(random.nextInt(BLOCK_SIZE));
            value.limit(value.position() + BLOCK_SIZE);
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        ByteBuffer expectedValue = ByteBuffer.allocate(BLOCK_SIZE * 2);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue.array());
            int valueOffset = random.nextInt(BLOCK_SIZE);
            value.clear().position(valueOffset).limit(valueOffset + BLOCK_SIZE);
            buffer.readBytes(value);
            assertEquals(valueOffset + BLOCK_SIZE, value.position());
            for (int j = valueOffset; j < valueOffset + BLOCK_SIZE; j++) {
                assertEquals(expectedValue.get(j), value.get(j));
            }
        }
    }

    @Test
    public void testSequentialCopiedBufferTransfer1() { //已阅读
        buffer.writerIndex(0);
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            byte[] value = new byte[BLOCK_SIZE];
            random.nextBytes(value);
            assertEquals(0, buffer.readerIndex());
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(value);
        }

        random.setSeed(seed);
        byte[] expectedValue = new byte[BLOCK_SIZE];
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            random.nextBytes(expectedValue);
            assertEquals(i, buffer.readerIndex());
            assertEquals(CAPACITY, buffer.writerIndex());
            ChannelBuffer actualValue = buffer.readBytes(BLOCK_SIZE);
            assertEquals(wrappedBuffer(expectedValue), actualValue);

            // Make sure if it is a copied buffer.
            actualValue.setByte(0, (byte) (actualValue.getByte(0) + 1));
            assertFalse(buffer.getByte(i) == actualValue.getByte(0));
        }
    }

    @Test
    public void testStreamTransfer1() throws Exception { //已阅读
        byte[] expected = new byte[buffer.capacity()];
        random.nextBytes(expected);

        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            ByteArrayInputStream in = new ByteArrayInputStream(expected, i, BLOCK_SIZE);
            assertEquals(BLOCK_SIZE, buffer.setBytes(i, in, BLOCK_SIZE));
            assertEquals(-1, buffer.setBytes(i, in, 0));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            buffer.getBytes(i, out, BLOCK_SIZE);
        }

        assertTrue(Arrays.equals(expected, out.toByteArray()));
    }

    @Test
    public void testStreamTransfer2() throws Exception { //已阅读
        byte[] expected = new byte[buffer.capacity()];
        random.nextBytes(expected);
        buffer.clear();

        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            ByteArrayInputStream in = new ByteArrayInputStream(expected, i, BLOCK_SIZE);
            assertEquals(i, buffer.writerIndex());
            buffer.writeBytes(in, BLOCK_SIZE);
            assertEquals(i + BLOCK_SIZE, buffer.writerIndex());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            assertEquals(i, buffer.readerIndex());
            buffer.readBytes(out, BLOCK_SIZE);
            assertEquals(i + BLOCK_SIZE, buffer.readerIndex());
        }

        assertTrue(Arrays.equals(expected, out.toByteArray()));
    }

    @Test
    public void testCopy() { //已阅读
        for (int i = 0; i < buffer.capacity(); i++) {
            byte value = (byte) random.nextInt();
            buffer.setByte(i, value);
        }

        final int readerIndex = CAPACITY / 3;
        final int writerIndex = CAPACITY * 2 / 3;
        buffer.setIndex(readerIndex, writerIndex);

        // Make sure all properties are copied.
        ChannelBuffer copy = buffer.copy();
        assertEquals(0, copy.readerIndex());
        assertEquals(buffer.readableBytes(), copy.writerIndex());
        assertEquals(buffer.readableBytes(), copy.capacity());
        for (int i = 0; i < copy.capacity(); i++) {
            assertEquals(buffer.getByte(i + readerIndex), copy.getByte(i));
        }

        // Make sure the buffer content is independent from each other.
        buffer.setByte(readerIndex, (byte) (buffer.getByte(readerIndex) + 1));
        assertTrue(buffer.getByte(readerIndex) != copy.getByte(0));
        copy.setByte(1, (byte) (copy.getByte(1) + 1));
        assertTrue(buffer.getByte(readerIndex + 1) != copy.getByte(1));
    }

    @Test
    public void testToByteBuffer1() { //已阅读
        byte[] value = new byte[buffer.capacity()];
        random.nextBytes(value);
        buffer.clear();
        buffer.writeBytes(value);

        assertEquals(ByteBuffer.wrap(value), buffer.toByteBuffer());
    }

    @Test
    public void testToByteBuffer2() { //已阅读
        byte[] value = new byte[buffer.capacity()];
        random.nextBytes(value);
        buffer.clear();
        buffer.writeBytes(value);

        for (int i = 0; i < buffer.capacity() - BLOCK_SIZE + 1; i += BLOCK_SIZE) {
            assertEquals(ByteBuffer.wrap(value, i, BLOCK_SIZE), buffer.toByteBuffer(i, BLOCK_SIZE));
        }
    }

    @Test
    public void testSkipBytes1() { //已阅读
        buffer.setIndex(CAPACITY / 4, CAPACITY / 2);

        buffer.skipBytes(CAPACITY / 4);
        assertEquals(CAPACITY / 4 * 2, buffer.readerIndex());

        try {
            buffer.skipBytes(CAPACITY / 4 + 1);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }

        // Should remain unchanged.
        assertEquals(CAPACITY / 4 * 2, buffer.readerIndex());
    }

}
