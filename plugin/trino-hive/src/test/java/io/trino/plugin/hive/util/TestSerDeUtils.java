/*
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
package io.trino.plugin.hive.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.Slice;
import io.airlift.slice.SliceOutput;
import io.airlift.slice.Slices;
import io.trino.block.BlockSerdeUtil;
import io.trino.spi.block.Block;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.block.BlockEncodingSerde;
import io.trino.spi.block.TestingBlockEncodingSerde;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import org.apache.hadoop.hive.common.type.Date;
import org.apache.hadoop.hive.common.type.Timestamp;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category;
import org.apache.hadoop.io.BytesWritable;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.hive.HiveTestUtils.mapType;
import static io.trino.plugin.hive.util.SerDeUtils.getBlockObject;
import static io.trino.plugin.hive.util.SerDeUtils.serializeObject;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DateType.DATE;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RealType.REAL;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.VarbinaryType.VARBINARY;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static io.trino.testing.StructuralTestUtil.arrayBlockOf;
import static io.trino.testing.StructuralTestUtil.mapBlockOf;
import static io.trino.testing.StructuralTestUtil.rowBlockOf;
import static java.lang.Math.toIntExact;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory.ObjectInspectorOptions;
import static org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory.getReflectionObjectInspector;
import static org.testng.Assert.assertEquals;

@SuppressWarnings("PackageVisibleField")
public class TestSerDeUtils
{
    private final BlockEncodingSerde blockEncodingSerde = new TestingBlockEncodingSerde();

    @SuppressWarnings("UnusedVariable") // these fields are serialized to a Block and verified there
    private static class ListHolder
    {
        List<InnerStruct> array;
    }

    @SuppressWarnings("UnusedVariable") // these fields are serialized to a Block and verified there
    private static class InnerStruct
    {
        public InnerStruct(Integer intVal, Long longVal)
        {
            this.intVal = intVal;
            this.longVal = longVal;
        }

        Integer intVal;
        Long longVal;
    }

    @SuppressWarnings("UnusedVariable") // these fields are serialized to a Block and verified there
    private static class OuterStruct
    {
        Byte byteVal;
        Short shortVal;
        Integer intVal;
        Long longVal;
        Float floatVal;
        Double doubleVal;
        String stringVal;
        byte[] byteArray;
        List<InnerStruct> structArray;
        Map<String, InnerStruct> map;
        InnerStruct innerStruct;
    }

    private static synchronized ObjectInspector getInspector(Type type)
    {
        // ObjectInspectorFactory.getReflectionObjectInspector is not thread-safe although it
        // gives people a first impression that it is. This may have been fixed in HIVE-11586.

        // Trino only uses getReflectionObjectInspector here, in a test method. Therefore, we
        // choose to work around this issue by synchronizing this method. Before synchronizing
        // this method, test in this class fails approximately 1 out of 10 runs on Travis.

        return getReflectionObjectInspector(type, ObjectInspectorOptions.JAVA);
    }

    @Test
    public void testPrimitiveSlice()
    {
        // boolean
        Block expectedBoolean = createSingleValue(BOOLEAN, blockBuilder -> BOOLEAN.writeBoolean(blockBuilder, true));
        Block actualBoolean = toBinaryBlock(BOOLEAN, true, getInspector(Boolean.class));
        assertBlockEquals(actualBoolean, expectedBoolean);

        // byte
        Block expectedByte = createSingleValue(TINYINT, blockBuilder -> TINYINT.writeLong(blockBuilder, 5));
        Block actualByte = toBinaryBlock(TINYINT, (byte) 5, getInspector(Byte.class));
        assertBlockEquals(actualByte, expectedByte);

        // short
        Block expectedShort = createSingleValue(SMALLINT, blockBuilder -> SMALLINT.writeLong(blockBuilder, 2));
        Block actualShort = toBinaryBlock(SMALLINT, (short) 2, getInspector(Short.class));
        assertBlockEquals(actualShort, expectedShort);

        // int
        Block expectedInt = createSingleValue(INTEGER, blockBuilder -> INTEGER.writeLong(blockBuilder, 1));
        Block actualInt = toBinaryBlock(INTEGER, 1, getInspector(Integer.class));
        assertBlockEquals(actualInt, expectedInt);

        // long
        Block expectedLong = createSingleValue(BIGINT, blockBuilder -> BIGINT.writeLong(blockBuilder, 10));
        Block actualLong = toBinaryBlock(BIGINT, 10L, getInspector(Long.class));
        assertBlockEquals(actualLong, expectedLong);

        // float
        Block expectedFloat = createSingleValue(REAL, blockBuilder -> REAL.writeLong(blockBuilder, Float.floatToIntBits(20.0f)));
        Block actualFloat = toBinaryBlock(REAL, 20.0f, getInspector(Float.class));
        assertBlockEquals(actualFloat, expectedFloat);

        // double
        Block expectedDouble = createSingleValue(DOUBLE, blockBuilder -> DOUBLE.writeDouble(blockBuilder, 30.12d));
        Block actualDouble = toBinaryBlock(DOUBLE, 30.12d, getInspector(Double.class));
        assertBlockEquals(actualDouble, expectedDouble);

        // string
        Block expectedString = createSingleValue(VARCHAR, blockBuilder -> VARCHAR.writeString(blockBuilder, "value"));
        Block actualString = toBinaryBlock(VARCHAR, "value", getInspector(String.class));
        assertBlockEquals(actualString, expectedString);

        // date
        int date = toIntExact(LocalDate.of(2008, 10, 28).toEpochDay());
        Block expectedDate = createSingleValue(DATE, blockBuilder -> DATE.writeLong(blockBuilder, date));
        Block actualDate = toBinaryBlock(DATE, Date.ofEpochDay(date), getInspector(Date.class));
        assertBlockEquals(actualDate, expectedDate);

        // timestamp
        DateTime dateTime = new DateTime(2008, 10, 28, 16, 7, 15, 123);
        Block expectedTimestamp = createSingleValue(TIMESTAMP_MILLIS, blockBuilder -> TIMESTAMP_MILLIS.writeLong(blockBuilder, dateTime.getMillis() * 1000));
        Block actualTimestamp = toBinaryBlock(TIMESTAMP_MILLIS, Timestamp.ofEpochMilli(dateTime.getMillis()), getInspector(Timestamp.class));
        assertBlockEquals(actualTimestamp, expectedTimestamp);

        // binary
        byte[] byteArray = {81, 82, 84, 85};
        Block expectedBinary = createSingleValue(VARBINARY, blockBuilder -> VARBINARY.writeSlice(blockBuilder, Slices.wrappedBuffer(byteArray)));
        Block actualBinary = toBinaryBlock(VARBINARY, byteArray, getInspector(byte[].class));
        assertBlockEquals(actualBinary, expectedBinary);
    }

    private static Block createSingleValue(io.trino.spi.type.Type type, Consumer<BlockBuilder> outputConsumer)
    {
        BlockBuilder blockBuilder = type.createBlockBuilder(null, 1);
        outputConsumer.accept(blockBuilder);
        return blockBuilder.build();
    }

    @Test
    public void testListBlock()
    {
        List<InnerStruct> array = new ArrayList<>(2);
        array.add(new InnerStruct(8, 9L));
        array.add(new InnerStruct(10, 11L));
        ListHolder listHolder = new ListHolder();
        listHolder.array = array;

        io.trino.spi.type.Type rowType = RowType.anonymous(ImmutableList.of(INTEGER, BIGINT));
        io.trino.spi.type.Type arrayOfRowType = RowType.anonymous(ImmutableList.of(new ArrayType(rowType)));
        Block actual = toBinaryBlock(arrayOfRowType, listHolder, getInspector(ListHolder.class));
        BlockBuilder blockBuilder = rowType.createBlockBuilder(null, 1024);
        rowType.writeObject(blockBuilder, rowBlockOf(ImmutableList.of(INTEGER, BIGINT), 8, 9L));
        rowType.writeObject(blockBuilder, rowBlockOf(ImmutableList.of(INTEGER, BIGINT), 10, 11L));
        Block expected = rowBlockOf(ImmutableList.of(new ArrayType(rowType)), blockBuilder.build());

        assertBlockEquals(actual, expected);
    }

    private static class MapHolder
    {
        Map<String, InnerStruct> map;
    }

    @Test
    public void testMapBlock()
    {
        MapHolder holder = new MapHolder();
        holder.map = new TreeMap<>();
        holder.map.put("twelve", new InnerStruct(13, 14L));
        holder.map.put("fifteen", new InnerStruct(16, 17L));

        RowType rowType = RowType.anonymous(ImmutableList.of(INTEGER, BIGINT));
        RowType rowOfMapOfVarcharRowType = RowType.anonymous(ImmutableList.of(mapType(VARCHAR, rowType)));
        Block actual = toBinaryBlock(rowOfMapOfVarcharRowType, holder, getInspector(MapHolder.class));

        Block mapBlock = mapBlockOf(
                VARCHAR,
                rowType,
                new Object[] {utf8Slice("fifteen"), utf8Slice("twelve")},
                new Object[] {rowBlockOf(rowType.getTypeParameters(), 16, 17L), rowBlockOf(rowType.getTypeParameters(), 13, 14L)});
        Block expected = rowBlockOf(ImmutableList.of(mapType(VARCHAR, rowType)), mapBlock);

        assertBlockEquals(actual, expected);
    }

    @Test
    public void testStructBlock()
    {
        // test simple structs
        InnerStruct innerStruct = new InnerStruct(13, 14L);

        io.trino.spi.type.Type rowType = RowType.anonymous(ImmutableList.of(INTEGER, BIGINT));
        Block actual = toBinaryBlock(rowType, innerStruct, getInspector(InnerStruct.class));

        Block expected = rowBlockOf(ImmutableList.of(INTEGER, BIGINT), 13, 14L);
        assertBlockEquals(actual, expected);

        // test complex structs
        OuterStruct outerStruct = new OuterStruct();
        outerStruct.byteVal = (byte) 1;
        outerStruct.shortVal = (short) 2;
        outerStruct.intVal = 3;
        outerStruct.longVal = 4L;
        outerStruct.floatVal = 5.01f;
        outerStruct.doubleVal = 6.001d;
        outerStruct.stringVal = "seven";
        outerStruct.byteArray = new byte[] {'2'};
        InnerStruct is1 = new InnerStruct(2, -5L);
        InnerStruct is2 = new InnerStruct(-10, 0L);
        outerStruct.structArray = new ArrayList<>(2);
        outerStruct.structArray.add(is1);
        outerStruct.structArray.add(is2);
        outerStruct.map = new TreeMap<>();
        outerStruct.map.put("twelve", new InnerStruct(0, 5L));
        outerStruct.map.put("fifteen", new InnerStruct(-5, -10L));
        outerStruct.innerStruct = new InnerStruct(18, 19L);

        io.trino.spi.type.Type innerRowType = RowType.anonymous(ImmutableList.of(INTEGER, BIGINT));
        io.trino.spi.type.Type arrayOfInnerRowType = new ArrayType(innerRowType);
        io.trino.spi.type.Type mapOfInnerRowType = mapType(createUnboundedVarcharType(), innerRowType);
        List<io.trino.spi.type.Type> outerRowParameterTypes = ImmutableList.of(TINYINT, SMALLINT, INTEGER, BIGINT, REAL, DOUBLE, createUnboundedVarcharType(), createUnboundedVarcharType(), arrayOfInnerRowType, mapOfInnerRowType, innerRowType);
        io.trino.spi.type.Type outerRowType = RowType.anonymous(outerRowParameterTypes);

        actual = toBinaryBlock(outerRowType, outerStruct, getInspector(OuterStruct.class));

        ImmutableList.Builder<Object> outerRowValues = ImmutableList.builder();
        outerRowValues.add((byte) 1);
        outerRowValues.add((short) 2);
        outerRowValues.add(3);
        outerRowValues.add(4L);
        outerRowValues.add(5.01f);
        outerRowValues.add(6.001d);
        outerRowValues.add("seven");
        outerRowValues.add(new byte[] {'2'});
        outerRowValues.add(arrayBlockOf(innerRowType, rowBlockOf(innerRowType.getTypeParameters(), 2, -5L), rowBlockOf(ImmutableList.of(INTEGER, BIGINT), -10, 0L)));
        outerRowValues.add(mapBlockOf(
                VARCHAR,
                innerRowType,
                new Object[] {utf8Slice("fifteen"), utf8Slice("twelve")},
                new Object[] {rowBlockOf(innerRowType.getTypeParameters(), -5, -10L), rowBlockOf(innerRowType.getTypeParameters(), 0, 5L)}));
        outerRowValues.add(rowBlockOf(ImmutableList.of(INTEGER, BIGINT), 18, 19L));

        assertBlockEquals(actual, rowBlockOf(outerRowParameterTypes, outerRowValues.build().toArray()));
    }

    @Test
    public void testReuse()
    {
        BytesWritable value = new BytesWritable();

        byte[] first = "hello world".getBytes(UTF_8);
        value.set(first, 0, first.length);

        byte[] second = "bye".getBytes(UTF_8);
        value.set(second, 0, second.length);

        Type type = new TypeToken<Map<BytesWritable, Long>>() {}.getType();
        ObjectInspector inspector = getInspector(type);

        Block actual = getBlockObject(mapType(createUnboundedVarcharType(), BIGINT), ImmutableMap.of(value, 0L), inspector);
        Block expected = mapBlockOf(createUnboundedVarcharType(), BIGINT, "bye", 0L);

        assertBlockEquals(actual, expected);
    }

    private void assertBlockEquals(Block actual, Block expected)
    {
        assertEquals(blockToSlice(actual), blockToSlice(expected));
    }

    private Slice blockToSlice(Block block)
    {
        // This function is strictly for testing use only
        SliceOutput sliceOutput = new DynamicSliceOutput(1000);
        BlockSerdeUtil.writeBlock(blockEncodingSerde, sliceOutput, block);
        return sliceOutput.slice();
    }

    private static Block toBinaryBlock(io.trino.spi.type.Type type, Object object, ObjectInspector inspector)
    {
        if (inspector.getCategory() == Category.PRIMITIVE) {
            return getPrimitiveBlock(type, object, inspector);
        }
        return getBlockObject(type, object, inspector);
    }

    private static Block getPrimitiveBlock(io.trino.spi.type.Type type, Object object, ObjectInspector inspector)
    {
        BlockBuilder builder = type.createBlockBuilder(null, 1);
        serializeObject(type, builder, object, inspector);
        return builder.build();
    }
}
