package com.jrjackson;

import java.io.IOException;

import com.fasterxml.jackson.core.*;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.util.ObjectBuffer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import org.jruby.Ruby;
import org.jruby.RubyObject;
import org.jruby.RubyArray;
import org.jruby.RubyHash;


public class RubyObjectDeserializer
        extends StdDeserializer<RubyObject> {

    private static final long serialVersionUID = 1L;

    private Ruby _ruby;

    private RubyKeyConverter key_converter;
    final private RubyStringConverter str_converter = new RubyStringConverter();

    public RubyObjectDeserializer() {
        super(RubyObject.class);
    }

    public RubyObjectDeserializer withRuby(Ruby ruby) {
        _ruby = ruby;
        return this;
    }

    public RubyObjectDeserializer setStringStrategy() {
        key_converter = new RubyStringNameConverter();
        return this;
    }

    public RubyObjectDeserializer setSymbolStrategy() {
        key_converter = new RubySymbolNameConverter();
        return this;
    }

    /**
     * /**********************************************************
     * /* Deserializer API /**********************************************************
     * @param jp
     * @param ctxt
     * @return 
     * @throws java.io.IOException
     * @throws com.fasterxml.jackson.core.JsonProcessingException
     */
    
    @Override
    public RubyObject deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        
        boolean use_big_decimal = ctxt.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        boolean use_big_integer = ctxt.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
        
        switch (jp.getCurrentToken()) {
            case START_OBJECT:
                return mapObject(jp, ctxt);

            case START_ARRAY:
                return mapArray(jp, ctxt);

            case FIELD_NAME:
                return key_converter.convert(_ruby, jp);

            case VALUE_EMBEDDED_OBJECT:
                return RubyUtils.rubyObject(_ruby, jp.getEmbeddedObject());

            case VALUE_STRING:
                return str_converter.convert(_ruby, jp);

            case VALUE_NUMBER_INT:
                /* [JACKSON-100]: caller may want to get all integral values
                 * returned as BigInteger, for consistency
                 */
                JsonParser.NumberType numberType = jp.getNumberType();
                if (use_big_integer || numberType == JsonParser.NumberType.BIG_INTEGER) {
                    return RubyUtils.rubyBignum(_ruby, jp.getBigIntegerValue());
                }
                return RubyUtils.rubyFixnum(_ruby, jp.getLongValue());

            case VALUE_NUMBER_FLOAT:
                if (use_big_decimal) {
                    return RubyUtils.rubyBigDecimal(_ruby, jp.getDecimalValue());
                }
                return RubyUtils.rubyFloat(_ruby, jp.getDoubleValue());

            case VALUE_TRUE:
                return _ruby.newBoolean(Boolean.TRUE);

            case VALUE_FALSE:
                return _ruby.newBoolean(Boolean.FALSE);

            case VALUE_NULL: // should not get this but...
                return (RubyObject) _ruby.getNil();

            case END_ARRAY: // invalid
            case END_OBJECT: // invalid
            default:
                throw ctxt.mappingException(Object.class);
        }
    }

    /**
     * /**********************************************************
     * /* Internal methods /**********************************************************
     */
    /**
     * Method called to map a JSON Array into a Java value.
     * @param jp
     * @param ctxt
     * @return RubyObject
     * @throws java.io.IOException 
     * @throws com.fasterxml.jackson.core.JsonProcessingException 
     */
    protected RubyObject mapArray(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
    // if (ctxt.isEnabled(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY)) {
        //     return mapArrayToArray(jp, ctxt);
        // }
        // Minor optimization to handle small lists (default size for ArrayList is 10)
        if (jp.nextToken() == JsonToken.END_ARRAY) {
            return RubyArray.newArray(_ruby);
        }
        ObjectBuffer buffer = ctxt.leaseObjectBuffer();
        Object[] values = buffer.resetAndStart();
        int ptr = 0;
        long totalSize = 0;
        do {
            Object value = deserialize(jp, ctxt);
            ++totalSize;
            if (ptr >= values.length) {
                values = buffer.appendCompletedChunk(values);
                ptr = 0;
            }
            values[ptr++] = value;
        } while (jp.nextToken() != JsonToken.END_ARRAY);
        // let's create almost full array, with 1/8 slack
        RubyArray result = RubyArray.newArray(_ruby, (totalSize + (totalSize >> 3) + 1));
        buffer.completeAndClearBuffer(values, ptr, result);
        return result;
    }

    /**
     * Method called to map a JSON Object into a Java value.
     * @param jp
     * @param ctxt
     * @return RubyObject
     * @throws java.io.IOException 
     * @throws com.fasterxml.jackson.core.JsonProcessingException 
     */
    protected RubyObject mapObject(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        JsonToken t = jp.getCurrentToken();
        if (t == JsonToken.START_OBJECT) {
            t = jp.nextToken();
        }
        // 1.6: minor optimization; let's handle 1 and 2 entry cases separately
        if (t != JsonToken.FIELD_NAME) { // and empty one too
            // empty map might work; but caller may want to modify... so better just give small modifiable
            return RubyHash.newHash(_ruby);
        }

        RubyObject field1 = key_converter.convert(_ruby, jp);
        jp.nextToken();
        RubyObject value1 = deserialize(jp, ctxt);
        
        if (jp.nextToken() != JsonToken.FIELD_NAME) { // single entry; but we want modifiable
            return RubyUtils.rubyHash(_ruby, field1, value1);
        }

        RubyObject field2 = key_converter.convert(_ruby, jp);
        jp.nextToken();
        
        RubyHash result =  RubyUtils.rubyHash(_ruby, field1, value1, field2, deserialize(jp, ctxt));
        
        if (jp.nextToken() != JsonToken.FIELD_NAME) {
            return  result;
        }

        // And then the general case; default map size is 16
        do {
            RubyObject fieldName = key_converter.convert(_ruby, jp);
            jp.nextToken();
            result.fastASetCheckString(_ruby, fieldName, deserialize(jp, ctxt));
        } while (jp.nextToken() != JsonToken.END_OBJECT);
        return result;
    }
}
