/*
 * Copyright (C) 2017-2018 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.sabot.op.aggregate.vectorized;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;

import com.dremio.sabot.op.common.ht2.LBlockHashTable;

import io.netty.buffer.ArrowBuf;
import io.netty.util.internal.PlatformDependent;

public class MaxAccumulators {

  private MaxAccumulators(){};

  public static class IntMaxAccumulator extends BaseSingleAccumulator {
    private static final long INIT = 0x8000000080000000l;
    private static final int WIDTH = 4;

    public IntMaxAccumulator(FieldVector input, FieldVector output) {
      super(input, output);
    }

    @Override
    void initialize(FieldVector vector) {
      setNullAndValue(vector, INIT);
    }

    public void accumulate(final long memoryAddr, final int count) {
      final long maxMemAddr = memoryAddr + count * 4;
      final long incomaxgBit = getInput().getValidityBufferAddress();
      final long incomaxgValue =  getInput().getDataBufferAddress();
      final long[] bitAddresses = this.bitAddresses;
      final long[] valueAddresses = this.valueAddresses;

      int incomaxgIndex = 0;
      for(long ordinalAddr = memoryAddr; ordinalAddr < maxMemAddr; ordinalAddr += 4, incomaxgIndex++){
        final int newVal = PlatformDependent.getInt(incomaxgValue + (incomaxgIndex * WIDTH));
        final int tableIndex = PlatformDependent.getInt(ordinalAddr);
        int chunkIndex = tableIndex >>> LBlockHashTable.BITS_IN_CHUNK;
        int chunkOffset = tableIndex & LBlockHashTable.CHUNK_OFFSET_MASK;
        final long maxAddr = valueAddresses[chunkIndex] + (chunkOffset) * 4;
        final long bitUpdateAddr = bitAddresses[chunkIndex] + ((chunkOffset >>> 5) * 4);
        final int bitVal = (PlatformDependent.getByte(incomaxgBit + ((incomaxgIndex >>> 3))) >>> (incomaxgIndex & 7)) & 1;
        final int bitUpdateVal = bitVal << (chunkOffset & 31);
        PlatformDependent.putInt(maxAddr, max(PlatformDependent.getInt(maxAddr), newVal, bitVal));
        PlatformDependent.putInt(bitUpdateAddr, PlatformDependent.getInt(bitUpdateAddr) | bitUpdateVal);
      }
    }
  }

  public static class FloatMaxAccumulator extends BaseSingleAccumulator {
    private static final int INIT = Float.floatToRawIntBits(-Float.MAX_VALUE);
    private static final int WIDTH = 4;

    public FloatMaxAccumulator(FieldVector input, FieldVector output) {
      super(input, output);
    }

    @Override
    void initialize(FieldVector vector) {
      setNullAndValue(vector, INIT);
    }

    public void accumulate(final long memoryAddr, final int count) {
      final long maxMemAddr = memoryAddr + count * 4;
      final long incomaxgBit = getInput().getValidityBufferAddress();
      final long incomaxgValue =  getInput().getDataBufferAddress();
      final long[] bitAddresses = this.bitAddresses;
      final long[] valueAddresses = this.valueAddresses;

      int incomaxgIndex = 0;
      for(long ordinalAddr = memoryAddr; ordinalAddr < maxMemAddr; ordinalAddr += 4, incomaxgIndex++){
        final float newVal = Float.intBitsToFloat(PlatformDependent.getInt(incomaxgValue + (incomaxgIndex * WIDTH)));
        final int tableIndex = PlatformDependent.getInt(ordinalAddr);
        int chunkIndex = tableIndex >>> LBlockHashTable.BITS_IN_CHUNK;
        int chunkOffset = tableIndex & LBlockHashTable.CHUNK_OFFSET_MASK;
        final long maxAddr = valueAddresses[chunkIndex] + (chunkOffset) * 4;
        final long bitUpdateAddr = bitAddresses[chunkIndex] + ((chunkOffset >>> 5) * 4);
        final int bitVal = (PlatformDependent.getByte(incomaxgBit + ((incomaxgIndex >>> 3))) >>> (incomaxgIndex & 7)) & 1;
        final int bitUpdateVal = bitVal << (chunkOffset & 31);
        PlatformDependent.putInt(maxAddr, Float.floatToRawIntBits(max(Float.intBitsToFloat(PlatformDependent.getInt(maxAddr)), newVal, bitVal)));
        PlatformDependent.putInt(bitUpdateAddr, PlatformDependent.getInt(bitUpdateAddr) | bitUpdateVal);
      }
    }
  }

  public static class BigIntMaxAccumulator extends BaseSingleAccumulator {

    private static final int WIDTH = 8;

    public BigIntMaxAccumulator(FieldVector input, FieldVector output) {
      super(input, output);
    }

    @Override
    void initialize(FieldVector vector) {
      setNullAndValue(vector, Long.MIN_VALUE);
    }

    public void accumulate(final long memoryAddr, final int count) {
      final long maxMemAddr = memoryAddr + count * 4;
      final long incomaxgBit = getInput().getValidityBufferAddress();
      final long incomaxgValue =  getInput().getDataBufferAddress();
      final long[] bitAddresses = this.bitAddresses;
      final long[] valueAddresses = this.valueAddresses;

      int incomaxgIndex = 0;
      for(long ordinalAddr = memoryAddr; ordinalAddr < maxMemAddr; ordinalAddr += 4, incomaxgIndex++){

        final long newVal = PlatformDependent.getLong(incomaxgValue + (incomaxgIndex * WIDTH));
        final int tableIndex = PlatformDependent.getInt(ordinalAddr);
        int chunkIndex = tableIndex >>> LBlockHashTable.BITS_IN_CHUNK;
        int chunkOffset = tableIndex & LBlockHashTable.CHUNK_OFFSET_MASK;
        final long maxAddr = valueAddresses[chunkIndex] + (chunkOffset) * 8;
        final long bitUpdateAddr = bitAddresses[chunkIndex] + ((chunkOffset >>> 5) * 4);
        final int bitVal = (PlatformDependent.getByte(incomaxgBit + ((incomaxgIndex >>> 3))) >>> (incomaxgIndex & 7)) & 1;
        final int bitUpdateVal = bitVal << (chunkOffset & 31);
        PlatformDependent.putLong(maxAddr, max(PlatformDependent.getLong(maxAddr), newVal, bitVal));
        PlatformDependent.putInt(bitUpdateAddr, PlatformDependent.getInt(bitUpdateAddr) | bitUpdateVal);
      }
    }
  }

  public static class DoubleMaxAccumulator extends BaseSingleAccumulator {

    private static final long INIT = Double.doubleToRawLongBits(-Double.MAX_VALUE);
    private static final int WIDTH = 8;

    public DoubleMaxAccumulator(FieldVector input, FieldVector output) {
      super(input, output);
    }

    @Override
    void initialize(FieldVector vector) {
      setNullAndValue(vector, INIT);
    }

    public void accumulate(final long memoryAddr, final int count) {
      final long maxMemAddr = memoryAddr + count * 4;
      final long incomaxgBit = getInput().getValidityBufferAddress();
      final long incomaxgValue =  getInput().getDataBufferAddress();
      final long[] bitAddresses = this.bitAddresses;
      final long[] valueAddresses = this.valueAddresses;

      int incomaxgIndex = 0;
      for(long ordinalAddr = memoryAddr; ordinalAddr < maxMemAddr; ordinalAddr += 4, incomaxgIndex++){
        final double newVal = Double.longBitsToDouble(PlatformDependent.getLong(incomaxgValue + (incomaxgIndex * WIDTH)));
        final int tableIndex = PlatformDependent.getInt(ordinalAddr);
        int chunkIndex = tableIndex >>> LBlockHashTable.BITS_IN_CHUNK;
        int chunkOffset = tableIndex & LBlockHashTable.CHUNK_OFFSET_MASK;
        final long maxAddr = valueAddresses[chunkIndex] + (chunkOffset) * 8;
        final long bitUpdateAddr = bitAddresses[chunkIndex] + ((chunkOffset >>> 5) * 4);
        final int bitVal = (PlatformDependent.getByte(incomaxgBit + ((incomaxgIndex >>> 3))) >>> (incomaxgIndex & 7)) & 1;
        final int bitUpdateVal = bitVal << (chunkOffset & 31);
        PlatformDependent.putLong(maxAddr, Double.doubleToRawLongBits(max(Double.longBitsToDouble(PlatformDependent.getLong(maxAddr)), newVal, bitVal)));
        PlatformDependent.putInt(bitUpdateAddr, PlatformDependent.getInt(bitUpdateAddr) | bitUpdateVal);
      }
    }
  }

  public static class DecimalMaxAccumulator extends BaseSingleAccumulator {
    private static final long INIT = Double.doubleToLongBits(-Double.MAX_VALUE);
    private static final int WIDTH_ORDINAL = 4;     // int ordinal #s
    private static final int WIDTH_INPUT = 16;      // decimal inputs
    private static final int WIDTH_ACCUMULATOR = 8; // double accumulators
    byte[] valBuf = new byte[WIDTH_INPUT];

    public DecimalMaxAccumulator(FieldVector input, FieldVector output) {
      super(input, output);
    }

    @Override
    void initialize(FieldVector vector) {
      setNullAndValue(vector, INIT);
    }

    public void accumulate(final long memoryAddr, final int count) {
      final long maxMemAddr = memoryAddr + count * WIDTH_ORDINAL;
      FieldVector inputVector = getInput();
      final long incomingBit = inputVector.getValidityBufferAddress();
      final long incomingValue = inputVector.getDataBufferAddress();
      final long[] bitAddresses = this.bitAddresses;
      final long[] valueAddresses = this.valueAddresses;
      final int scale = ((DecimalVector)inputVector).getScale();

      int incomingIndex = 0;
      for(long ordinalAddr = memoryAddr; ordinalAddr < maxMemAddr; ordinalAddr += WIDTH_ORDINAL, incomingIndex++){
        java.math.BigDecimal newVal = DecimalAccumulatorUtils.getBigDecimal(incomingValue + (incomingIndex * WIDTH_INPUT), valBuf, scale);
        final int tableIndex = PlatformDependent.getInt(ordinalAddr);
        int chunkIndex = tableIndex >>> LBlockHashTable.BITS_IN_CHUNK;
        int chunkOffset = tableIndex & LBlockHashTable.CHUNK_OFFSET_MASK;
        final long maxAddr = valueAddresses[chunkIndex] + (chunkOffset) * WIDTH_ACCUMULATOR;
        final long bitUpdateAddr = bitAddresses[chunkIndex] + ((chunkOffset >>> 5) * 4);
        final int bitVal = (PlatformDependent.getByte(incomingBit + ((incomingIndex >>> 3))) >>> (incomingIndex & 7)) & 1;
        final int bitUpdateVal = bitVal << (chunkOffset & 31);
        PlatformDependent.putLong(maxAddr, Double.doubleToLongBits(max(Double.longBitsToDouble(PlatformDependent.getLong(maxAddr)), newVal.doubleValue(), bitVal)));
        PlatformDependent.putInt(bitUpdateAddr, PlatformDependent.getInt(bitUpdateAddr) | bitUpdateVal);
      }
    }
  }

  public static class BitMaxAccumulator extends BaseSingleAccumulator {
    private static final int WIDTH_LONG = 8;        // operations done on long boundaries
    private static final int BITS_PER_LONG_SHIFT = 6;  // (1<<6) bits per long
    private static final int BITS_PER_LONG = (1<<BITS_PER_LONG_SHIFT);
    private static final int WIDTH_ORDINAL = 4;     // int ordinal #s

    public BitMaxAccumulator(FieldVector input, FieldVector output) {
      super(input, output);
    }

    @Override
    void initialize(FieldVector vector) {
      setNullAndZero(vector);
    }

    public void accumulate(final long memoryAddr, final int count) {
      List<ArrowBuf> buffers = getInput().getFieldBuffers();
      final long incomingBit = buffers.get(0).memoryAddress();
      final long incomingValue = buffers.get(1).memoryAddress();
      final long[] bitAddresses = this.bitAddresses;
      final long[] valueAddresses = this.valueAddresses;

      final long numWords = (count + (BITS_PER_LONG-1)) >>> BITS_PER_LONG_SHIFT; // rounded up number of words that cover 'count' bits
      final long maxInputAddr = incomingValue + numWords * WIDTH_LONG;
      final long maxOrdinalAddr = memoryAddr + count * WIDTH_ORDINAL;

      // Like every accumulator, the code below essentially implements:
      //   accumulators[ordinals[i]] += inputs[i]
      // with the only complication that both accumulators and inputs are bits.
      // There's nothing we can do about the locality of the accumulators, but inputs can be processed a word at a time.
      // Algorithm:
      // - get 64 bits worth of inputs, until all inputs exhausted. For each long:
      //   - find the accumulator word it pertains to
      //   - read/update/write the accumulator bit
      // In the code below:
      // - input* refers to the data values in the incoming batch
      // - ordinal* refers to the temporary table that hashAgg passes in, identifying which hash table entry each input matched to
      // - min* refers to the accumulator
      for (long inputAddr = incomingValue, inputBitAddr = incomingBit, batchCount = 0;
           inputAddr < maxInputAddr;
           inputAddr += WIDTH_LONG, inputBitAddr += WIDTH_LONG, batchCount++) {
        final long inputBatch = PlatformDependent.getLong(inputAddr);
        final long inputBits = PlatformDependent.getLong(inputBitAddr);
        long ordinalAddr = memoryAddr + (batchCount << BITS_PER_LONG_SHIFT);
        for (long bitNum = 0; bitNum < BITS_PER_LONG && ordinalAddr < maxOrdinalAddr; bitNum++, ordinalAddr += WIDTH_ORDINAL) {
          final int tableIndex = PlatformDependent.getInt(ordinalAddr);
          int chunkIndex = tableIndex >>> LBlockHashTable.BITS_IN_CHUNK;
          int chunkOffset = tableIndex & LBlockHashTable.CHUNK_OFFSET_MASK;
          final long minBitUpdateAddr = bitAddresses[chunkIndex] + ((chunkOffset >>> 5) * 4);
          // Update rules:
          // max of two boolean values boils down to doing a bitwise OR on the two
          // If the input bit is set, we update both the accumulator value and its bit
          //    -- the accumulator is OR-ed with the value of the input bit
          //    -- the accumulator bit is OR-ed with 1 (since the input is valid)
          // If the input bit is not set, we update neither the accumulator nor its bit
          //    -- the accumulator is OR-ed with a 0 (thus remaining unchanged)
          //    -- the accumulator bit is OR-ed with 0 (thus remaining unchanged)
          // Thus, the logical function for updating the accumulator is: oldVal OR (inputBit AND inputValue)
          // Thus, the logical function for updating the accumulator is: oldBitVal OR inputBit
          // Because the operations are all done in a word length (and not on an individual bit), the AND value for
          // updating the accumulator must have all its other bits set to 1
          final int inputBitVal = (int)((inputBits >>> bitNum) & 0x01);
          final int inputVal = (int)((inputBatch >>> bitNum) & 0x01);
          final int minBitUpdateVal = inputBitVal << (chunkOffset & 31);
          int minUpdateVal = (inputBitVal & inputVal) << (chunkOffset & 31);
          final long minAddr = valueAddresses[chunkIndex] + ((chunkOffset >>> 5) * 4);
          PlatformDependent.putInt(minAddr, PlatformDependent.getInt(minAddr) | minUpdateVal);
          PlatformDependent.putInt(minBitUpdateAddr, PlatformDependent.getInt(minBitUpdateAddr) | minBitUpdateVal);
        }
      }
    }
  }

  public static class IntervalDayMaxAccumulator extends BaseSingleAccumulator {
    private static final long INIT = 0x8000000080000000l;
    private static final int WIDTH_ORDINAL = 4;     // int ordinal #s
    private static final int WIDTH_INPUT = 8;       // pair-of-ints inputs
    private static final int WIDTH_ACCUMULATOR = 8; // pair-of-ints pair accumulators

    public IntervalDayMaxAccumulator(FieldVector input, FieldVector output) {
      super(input, output);
    }

    @Override
    void initialize(FieldVector vector) {
      setNullAndValue(vector, INIT);
    }

    public void accumulate(final long memoryAddr, final int count) {
      final long maxAddr = memoryAddr + count * WIDTH_ORDINAL;
      List<ArrowBuf> buffers = getInput().getFieldBuffers();
      final long incomingBit = buffers.get(0).memoryAddress();
      final long incomingValue = buffers.get(1).memoryAddress();
      final long[] bitAddresses = this.bitAddresses;
      final long[] valueAddresses = this.valueAddresses;

      int incomingIndex = 0;
      for(long ordinalAddr = memoryAddr; ordinalAddr < maxAddr; ordinalAddr += WIDTH_ORDINAL, incomingIndex++){
        final long newVal = PlatformDependent.getLong(incomingValue + (incomingIndex * WIDTH_INPUT));
        final int tableIndex = PlatformDependent.getInt(ordinalAddr);
        int chunkIndex = tableIndex >>> LBlockHashTable.BITS_IN_CHUNK;
        int chunkOffset = tableIndex & LBlockHashTable.CHUNK_OFFSET_MASK;
        final long minAddr = valueAddresses[chunkIndex] + (chunkOffset) * WIDTH_ACCUMULATOR;
        final long bitUpdateAddr = bitAddresses[chunkIndex] + ((chunkOffset >>> 5) * 4);
        final int bitVal = (PlatformDependent.getByte(incomingBit + ((incomingIndex >>> 3))) >>> (incomingIndex & 7)) & 1;
        final int bitUpdateVal = bitVal << (chunkOffset & 31);
        // first 4 bytes are the number of days (in little endian, that's the bottom 32 bits)
        // second 4 bytes are the number of milliseconds (in little endian, that's the top 32 bits)
        final int newDays = (int) newVal;
        final int newMillis = (int)(newVal >> 32);
        // To compare the pairs of day/milli, we swap them, with days getting the most significant bits
        // The incoming value is updated to either be MAX (if incoming is null), or keep as is (if the value is not null)
        final long newSwappedVal = ((((long)newDays) << 32) | newMillis) * bitVal + Long.MAX_VALUE * (bitVal ^ 1);
        final long maxVal = PlatformDependent.getLong(minAddr);
        final int maxDays = (int) maxVal;
        final int maxMillis = (int)(maxVal >> 32);
        final long maxSwappedVal = (((long)maxDays) << 32) | maxMillis;
        PlatformDependent.putLong(minAddr, (maxSwappedVal > newSwappedVal) ? maxVal : newVal);
        PlatformDependent.putInt(bitUpdateAddr, PlatformDependent.getInt(bitUpdateAddr) | bitUpdateVal);
      }
    }
  }

  private static final long max(long a, long b, int bitVal){
    // update the incoming value to either be the max (if the incoming is null) or keep as is (if the value is not null)
    b = b * bitVal + Long.MIN_VALUE * (bitVal ^ 1);
    return Math.max(a,b);
  }

  private static final int max(int a, int b, int bitVal){
    b = b * bitVal + Integer.MIN_VALUE * (bitVal ^ 1);
    return Math.max(a,b);
  }

  private static final double max(double a, double b, int bitVal){
    if(bitVal == 1){
      return Math.max(a, b);
    }
    return a;
  }

  private static final float max(float a, float b, int bitVal){
    if(bitVal == 1){
      return Math.max(a, b);
    }
    return a;
  }
}
