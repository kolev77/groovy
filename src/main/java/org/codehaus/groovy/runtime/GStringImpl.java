/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.codehaus.groovy.runtime;

import groovy.lang.GString;
import groovy.lang.GroovyObject;
import groovy.transform.Pure;
import org.apache.groovy.ast.tools.ImmutablePropertyUtils;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Default implementation of a GString used by the compiler. A GString consists
 * of a list of values and strings which can be combined to create a new String.
 *
 * @see groovy.lang.GString
 */
public class GStringImpl extends GString {
    private static final long serialVersionUID = 3581289038662723858L;
    private final String[] strings;
    private final boolean frozen;
    private boolean cacheable;
    private String cachedStringLiteral;

    /**
     * Create a new GString with values and strings.
     * <p>
     * Each value is prefixed by a string, after the last value
     * an additional String might be used, hence the following constraint is expected to hold:
     * <code>
     * strings.length == values.length  ||  strings.length == values.length + 1
     * </code>.
     * <p>
     * <strong>NOTE:</strong> The lengths are <strong>not</strong> checked but using arrays with
     * lengths which violate the above constraint could result in unpredictable behaviour.
     *
     * @param values  the value parts
     * @param strings the string parts
     */
    public GStringImpl(Object[] values, String[] strings) {
        this(values, strings, checkValuesStringConstant(values), null, false);
    }

    /**
     * Create a new GString with values and strings.
     * <p>
     * Each value is prefixed by a string, after the last value
     * an additional String might be used, hence the following constraint is expected to hold:
     * <code>
     * strings.length == values.length  ||  strings.length == values.length + 1
     * </code>.
     * <p>
     * <strong>NOTE:</strong> The lengths are <strong>not</strong> checked but using arrays with
     * lengths which violate the above constraint could result in unpredictable behaviour.
     *
     * @param values  the value parts
     * @param strings the string parts
     * @param frozen  creates a GStringImpl which is not subject to mutation and hence more amenable to caching
     */
    protected GStringImpl(Object[] values, String[] strings, boolean cacheable, String cachedStringLiteral, boolean frozen) {
        super(frozen ? values.clone() : values);
        this.strings = frozen ? strings.clone() : strings;
        this.cacheable = cacheable;
        this.cachedStringLiteral = cachedStringLiteral;
        this.frozen = frozen;
    }

    @Override
    public GString plus(GString that) {
        GString thatFrozen = that instanceof GStringImpl ? ((GStringImpl) that).freeze() : that;
        return GStringUtil.plusImpl(super.getValues(), thatFrozen.getValues(), strings, thatFrozen.getStrings());
    }

    @Override
    public Writer writeTo(Writer out) throws IOException {
        return GStringUtil.writeToImpl(out, super.getValues(), strings);
    }

    /* (non-Javadoc)
     * @see groovy.lang.Buildable#build(groovy.lang.GroovyObject)
     */
    @Override
    public void build(final GroovyObject builder) {
        GStringUtil.buildImpl(builder, super.getValues(), strings);
    }

    @Override
    protected int calcInitialCapacity() {
        return GStringUtil.calcInitialCapacityImpl(super.getValues(), strings);
    }

    /**
     * @return returns an equivalent optimised but less mutable version of this GString
     */
    public GString freeze() {
        return new GStringImpl(super.getValues(), strings, cacheable, cachedStringLiteral, true);
    }

    /**
     * Get the strings of this GString.
     * <p>
     * This method returns the same array as used in the constructor.
     * Changing the values will result in changes of the GString.
     * It is generally not recommended to do so.
     */
    @Override
    public String[] getStrings() {
        if (frozen) {
            return strings.clone();
        }
        cacheable = false;
        cachedStringLiteral = null;
        return strings;
    }

    @Override
    public Object[] getValues() {
        if (frozen) {
            return super.getValues().clone();
        }
        cacheable = false;
        cachedStringLiteral = null;
        return super.getValues();
    }

    public String trim() {
        return toString().trim();
    }

    public boolean isEmpty() {
        return toString().isEmpty();
    }

    /**
     * Returns a stream of lines extracted from this string,
     * separated by line terminators.
     * <p>
     * A <i>line terminator</i> is one of the following:
     * a line feed character {@code "\n"} (U+000A),
     * a carriage return character {@code "\r"} (U+000D),
     * or a carriage return followed immediately by a line feed
     * {@code "\r\n"} (U+000D U+000A).
     * <p>
     * A <i>line</i> is either a sequence of zero or more characters
     * followed by a line terminator, or it is a sequence of one or
     * more characters followed by the end of the string. A
     * line does not include the line terminator.
     * <p>
     * The stream returned by this method contains the lines from
     * this string in the order in which they occur.
     *
     * @apiNote This definition of <i>line</i> implies that an empty
     *          string has zero lines and that there is no empty line
     *          following a line terminator at the end of a string.
     *
     * @implNote This method provides better performance than
     *           split("\R") by supplying elements lazily and
     *           by faster search of new line terminators.
     *
     * @return  the stream of lines extracted from this string
     *
     * @since 5.0.0
     */
    public Stream<String> lines() {
        return toString().lines();
    }

    /**
     * Returns {@code true} if the string is empty or contains only
     * {@linkplain Character#isWhitespace(int) white space} codepoints,
     * otherwise {@code false}.
     *
     * @return {@code true} if the string is empty or contains only
     *         {@linkplain Character#isWhitespace(int) white space} codepoints,
     *         otherwise {@code false}
     *
     * @see Character#isWhitespace(int)
     *
     * @since 5.0.0
     */
    public boolean isBlank() {
        return toString().isBlank();
    }

    /**
     * Returns a string whose value is the concatenation of this
     * string repeated {@code count} times.
     * <p>
     * If this string is empty or count is zero then the empty
     * string is returned.
     *
     * @param   count number of times to repeat
     *
     * @return  A string composed of this string repeated
     *          {@code count} times or the empty string if this
     *          string is empty or count is zero
     *
     * @throws  IllegalArgumentException if the {@code count} is
     *          negative.
     *
     * @since 5.0.0
     */
    public String repeat(int count) {
        return toString().repeat(count);
    }

    /**
     * Returns a string whose value is this string, with all leading
     * {@linkplain Character#isWhitespace(int) white space} removed.
     * <p>
     * If this {@code String} object represents an empty string,
     * or if all code points in this string are
     * {@linkplain Character#isWhitespace(int) white space}, then an empty string
     * is returned.
     * <p>
     * Otherwise, returns a substring of this string beginning with the first
     * code point that is not a {@linkplain Character#isWhitespace(int) white space}
     * up to and including the last code point of this string.
     * <p>
     * This method may be used to trim
     * {@linkplain Character#isWhitespace(int) white space} from
     * the beginning of a string.
     *
     * @return  a string whose value is this string, with all leading white
     *          space removed
     *
     * @see Character#isWhitespace(int)
     *
     * @since 5.0.0
     */
    public String stripLeading() {
        return toString().stripLeading();
    }

    /**
     * Returns a string whose value is this string, with all trailing
     * {@linkplain Character#isWhitespace(int) white space} removed.
     * <p>
     * If this {@code String} object represents an empty string,
     * or if all characters in this string are
     * {@linkplain Character#isWhitespace(int) white space}, then an empty string
     * is returned.
     * <p>
     * Otherwise, returns a substring of this string beginning with the first
     * code point of this string up to and including the last code point
     * that is not a {@linkplain Character#isWhitespace(int) white space}.
     * <p>
     * This method may be used to trim
     * {@linkplain Character#isWhitespace(int) white space} from
     * the end of a string.
     *
     * @return  a string whose value is this string, with all trailing white
     *          space removed
     *
     * @see Character#isWhitespace(int)
     *
     * @since 5.0.0
     */
    public String stripTrailing() {
        return toString().stripTrailing();
    }

    /**
     * Returns a string whose value is this string, with all leading
     * and trailing {@linkplain Character#isWhitespace(int) white space}
     * removed.
     * <p>
     * If this {@code String} object represents an empty string,
     * or if all code points in this string are
     * {@linkplain Character#isWhitespace(int) white space}, then an empty string
     * is returned.
     * <p>
     * Otherwise, returns a substring of this string beginning with the first
     * code point that is not a {@linkplain Character#isWhitespace(int) white space}
     * up to and including the last code point that is not a
     * {@linkplain Character#isWhitespace(int) white space}.
     * <p>
     * This method may be used to strip
     * {@linkplain Character#isWhitespace(int) white space} from
     * the beginning and end of a string.
     *
     * @return  a string whose value is this string, with all leading
     *          and trailing white space removed
     *
     * @see Character#isWhitespace(int)
     *
     * @since 5.0.0
     */
    public String strip() {
        return toString().strip();
    }

    public int codePointAt(int index) {
        return toString().codePointAt(index);
    }

    public int codePointBefore(int index) {
        return toString().codePointBefore(index);
    }

    public int codePointCount(int beginIndex, int endIndex) {
        return toString().codePointCount(beginIndex, endIndex);
    }

    public int offsetByCodePoints(int index, int codePointOffset) {
        return toString().offsetByCodePoints(index, codePointOffset);
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        toString().getChars(srcBegin, srcEnd, dst, dstBegin);
    }

    public byte[] getBytes(Charset charset) {
        return toString().getBytes(charset);
    }

    public boolean contentEquals(StringBuffer sb) {
        return toString().contentEquals(sb);
    }

    public boolean contentEquals(CharSequence cs) {
        return toString().contentEquals(cs);
    }

    public boolean equalsIgnoreCase(String anotherString) {
        return toString().equalsIgnoreCase(anotherString);
    }

    public int compareTo(String anotherString) {
        return toString().compareTo(anotherString);
    }

    public int compareToIgnoreCase(String str) {
        return toString().compareToIgnoreCase(str);
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
        return toString().regionMatches(toffset, other, ooffset, len);
    }

    public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
        return toString().regionMatches(ignoreCase, toffset, other, ooffset, len);
    }

    public boolean startsWith(String prefix, int toffset) {
        return toString().startsWith(prefix, toffset);
    }

    public boolean startsWith(String prefix) {
        return toString().startsWith(prefix);
    }

    public boolean endsWith(String suffix) {
        return toString().endsWith(suffix);
    }

    public int indexOf(int ch) {
        return toString().indexOf(ch);
    }

    public int indexOf(int ch, int fromIndex) {
        return toString().indexOf(ch, fromIndex);
    }

    public int lastIndexOf(int ch) {
        return toString().lastIndexOf(ch);
    }

    public int lastIndexOf(int ch, int fromIndex) {
        return toString().lastIndexOf(ch, fromIndex);
    }

    public int indexOf(String str) {
        return toString().indexOf(str);
    }

    public int indexOf(String str, int fromIndex) {
        return toString().indexOf(str, fromIndex);
    }

    public int lastIndexOf(String str) {
        return toString().lastIndexOf(str);
    }

    public int lastIndexOf(String str, int fromIndex) {
        return toString().lastIndexOf(str, fromIndex);
    }

    public String substring(int beginIndex) {
        return toString().substring(beginIndex);
    }

    public String substring(int beginIndex, int endIndex) {
        return toString().substring(beginIndex, endIndex);
    }

    public String concat(String str) {
        return toString().concat(str);
    }

    public String replace(char oldChar, char newChar) {
        return toString().replace(oldChar, newChar);
    }

    public boolean matches(String regex) {
        return toString().matches(regex);
    }

    public boolean contains(CharSequence s) {
        return toString().contains(s);
    }

    public String replaceFirst(String regex, String replacement) {
        return toString().replaceFirst(regex, replacement);
    }

    public String replaceAll(String regex, String replacement) {
        return toString().replaceAll(regex, replacement);
    }

    public String replace(CharSequence target, CharSequence replacement) {
        return toString().replace(target, replacement);
    }

    public String[] split(String regex, int limit) {
        return toString().split(regex, limit);
    }

    public String[] split(String regex) {
        return toString().split(regex);
    }

    public String toLowerCase(Locale locale) {
        return toString().toLowerCase(locale);
    }

    public String toLowerCase() {
        return toString().toLowerCase();
    }

    public String toUpperCase(Locale locale) {
        return toString().toUpperCase(locale);
    }

    public String toUpperCase() {
        return toString().toUpperCase();
    }

    public char[] toCharArray() {
        return toString().toCharArray();
    }

    public String intern() {
        return toString().intern();
    }

    @Override
    public String toString() {
        if (null != cachedStringLiteral) {
            return cachedStringLiteral;
        }
        String str = super.toString();
        if (cacheable) {
            cachedStringLiteral = str;
        }
        return str;
    }

    private static boolean checkValuesStringConstant(Object[] values) {
        for (Object value : values) {
            if (null == value) continue;
            if (!(ImmutablePropertyUtils.builtinOrMarkedImmutableClass(value.getClass())
                    || toStringMarkedPure(value.getClass())
                    || (value instanceof GStringImpl && ((GStringImpl) value).cacheable))) {
                return false;
            }
        }

        return true;
    }

    private static boolean toStringMarkedPure(Class<?> clazz) {
        Method toStringMethod;
        try {
            toStringMethod = clazz.getMethod("toString");
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
        return toStringMethod.isAnnotationPresent(Pure.class);
    }
}
