/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static com.wentch.redkale.source.FilterExpress.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/**
 *
 * @author zhangjx
 */
public class FilterNode {

    protected boolean signand = true;

    protected String tabalis;

    protected String column;

    protected FilterExpress express;

    protected boolean likefit = true;

    protected FilterNode[] nodes;

    private Serializable value;

    public FilterNode() {
    }

    FilterNode(String col, FilterExpress exp, Serializable val) {
        this(col, exp, true, val);
    }

    FilterNode(String col, FilterExpress exp, boolean likefit, Serializable val) {
        Objects.requireNonNull(col);
        if (exp == null) {
            if (val instanceof Range) {
                exp = FilterExpress.BETWEEN;
            } else if (val instanceof Collection) {
                exp = FilterExpress.IN;
            } else if (val != null && val.getClass().isArray()) {
                exp = FilterExpress.IN;
            } else {
                exp = FilterExpress.EQUAL;
            }
        }
        this.column = col;
        this.express = exp;
        this.likefit = likefit;
        this.value = val;
    }

    public final FilterNode and(FilterNode node) {
        return any(node, true);
    }

    public final FilterNode and(String column, Serializable value) {
        return and(new FilterNode(column, null, value));
    }

    public final FilterNode and(String column, FilterExpress express, Serializable value) {
        return and(new FilterNode(column, express, value));
    }

    public final FilterNode and(String column, FilterExpress express, boolean likefit, Serializable value) {
        return and(new FilterNode(column, express, likefit, value));
    }

    public final FilterNode or(FilterNode node) {
        return any(node, false);
    }

    public final FilterNode or(String column, Serializable value) {
        return or(new FilterNode(column, null, value));
    }

    public final FilterNode or(String column, FilterExpress express, Serializable value) {
        return or(new FilterNode(column, express, value));
    }

    public final FilterNode or(String column, FilterExpress express, boolean likefit, Serializable value) {
        return or(new FilterNode(column, express, likefit, value));
    }

    protected final FilterNode any(FilterNode node, boolean sign) {
        Objects.requireNonNull(node);
        if (nodes == null) {
            nodes = new FilterNode[]{node};
            this.signand = sign;
            return this;
        }
        if (signand == sign) {
            FilterNode[] newsiblings = new FilterNode[nodes.length + 1];
            System.arraycopy(nodes, 0, newsiblings, 0, nodes.length);
            newsiblings[nodes.length] = node;
            this.nodes = newsiblings;
            return this;
        }
        this.append(node, sign);
        return this;
    }

    protected void append(FilterNode node, boolean sign) {
        FilterNode newnode = new FilterNode(this.column, this.express, this.value);
        newnode.signand = this.signand;
        newnode.nodes = this.nodes;
        newnode.likefit = this.likefit;
        this.nodes = new FilterNode[]{newnode, node};
        this.tabalis = null;
        this.column = null;
        this.express = null;
        this.likefit = false;
        this.signand = sign;
        this.value = null;
    }

    protected Serializable getValue(FilterBean bean) {
        return value;
    }

    protected boolean isJoinAllCached() {
        return true;
    }

    public static FilterNode create(String column, Serializable value) {
        return create(column, null, value);
    }

    public static FilterNode create(String column, FilterExpress express, Serializable value) {
        return new FilterNode(column, express, value);
    }

    protected final <T> StringBuilder createFilterSQLExpress(final EntityInfo<T> info, FilterBean bean) {
        return createFilterSQLExpress(true, info, bean);
    }

    protected <T> StringBuilder createFilterSQLExpress(final boolean first, final EntityInfo<T> info, FilterBean bean) {
        final Serializable val = getValue(bean);
        if (val == null && (express == ISNULL || express == ISNOTNULL)) return new StringBuilder(0);
        StringBuilder sb0 = createFilterSQLExpress(info, val);
        if (this.nodes == null) {
            if (sb0 == null) return new StringBuilder(0);
            if (!first) return sb0;
            return new StringBuilder(sb0.length() + 8).append(" WHERE ").append(sb0);
        }
        final StringBuilder rs = new StringBuilder();
        rs.append(first ? " WHERE (" : " (");
        boolean more = false;
        if (sb0 != null && sb0.length() > 2) {
            more = true;
            rs.append(sb0);
        }
        for (FilterNode node : this.nodes) {
            StringBuilder f = node.createFilterSQLExpress(false, info, bean);
            if (f == null || f.length() < 3) continue;
            if (more) rs.append(signand ? " AND " : " OR ");
            rs.append(f);
            more = true;
        }
        rs.append(')');
        if (rs.length() < (first ? 10 : 5)) return new StringBuilder(0);
        return rs;
    }

    private <T> StringBuilder createFilterSQLExpress(final EntityInfo<T> info, Serializable val0) {
        if (column == null) return null;
        final StringBuilder val = formatValue(val0);
        if (val == null) return null;
        StringBuilder sb = new StringBuilder();
        if (tabalis != null) sb.append(tabalis).append('.');
        sb.append(info.getSQLColumn(column)).append(' ');
        switch (express) {
            case ISNULL:
            case ISNOTNULL:
                sb.append(express.value());
                break;
            case OPAND:
            case OPOR:
                sb.append(express.value()).append(' ').append(val).append(" > 0");
                break;
            case OPANDNO:
                sb.append(express.value()).append(' ').append(val).append(" = 0");
                break;
            default:
                sb.append(express.value()).append(' ').append(val);
                break;
        }
        return sb;
    }

    protected static <E> String createFilterSQLOrderBy(EntityInfo<E> info, Flipper flipper) {
        if (flipper == null || flipper.getSort() == null || flipper.getSort().isEmpty()) return "";
        final StringBuilder sb = new StringBuilder();
        sb.append(" ORDER BY ");
        if (info.isNoAlias()) {
            sb.append(flipper.getSort());
        } else {
            boolean flag = false;
            for (String item : flipper.getSort().split(",")) {
                if (item.isEmpty()) continue;
                String[] sub = item.split("\\s+");
                if (flag) sb.append(',');
                if (sub.length < 2 || sub[1].equalsIgnoreCase("ASC")) {
                    sb.append("a.").append(info.getSQLColumn(sub[0])).append(" ASC");
                } else {
                    sb.append("a.").append(info.getSQLColumn(sub[0])).append(" DESC");
                }
                flag = true;
            }
        }
        return sb.toString();
    }

    protected <T> Predicate<T> createFilterPredicate(final EntityInfo<T> info, FilterBean bean) {
        if (info == null || (column == null && this.nodes == null)) return null;
        final Serializable val = getValue(bean);
        Predicate<T> filter = (val == null || column == null) ? null : createFilterPredicate(info.getAttribute(column), val);
        if (this.nodes == null) return filter;
        for (FilterNode node : this.nodes) {
            Predicate<T> f = node.createFilterPredicate(info, bean);
            if (f == null) continue;
            final Predicate<T> one = filter;
            final Predicate<T> two = f;
            filter = (filter == null) ? f : (signand ? new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return one.test(t) && two.test(t);
                }

                @Override
                public String toString() {
                    return "(" + one + " AND " + two + ")";
                }
            } : new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return one.test(t) || two.test(t);
                }

                @Override
                public String toString() {
                    return "(" + one + " OR " + two + ")";
                }
            });
        }
        return filter;
    }

    protected final <T> Predicate<T> createFilterPredicate(final Attribute<T, Serializable> attr, Serializable val0) {
        if (attr == null) return null;
        final Class atype = attr.type();
        if (val0 != null && atype != val0.getClass() && val0 instanceof Number) {
            if (atype == short.class || atype == Short.class) {
                val0 = ((Number) val0).shortValue();
            } else if (atype == long.class || atype == Long.class) {
                val0 = ((Number) val0).longValue();
            } else if (atype == byte.class || atype == Byte.class) {
                val0 = ((Number) val0).byteValue();
            } else if (atype == int.class || atype == Integer.class) {
                val0 = ((Number) val0).intValue();
            } else if (atype == float.class || atype == Float.class) {
                val0 = ((Number) val0).floatValue();
            } else if (atype == double.class || atype == Double.class) {
                val0 = ((Number) val0).doubleValue();
            }
        }
        final Serializable val = val0;
        switch (express) {
            case EQUAL: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return val.equals(attr.get(t));
                }

                @Override
                public String toString() {
                    return attr.field() + ' ' + express.value() + ' ' + val;
                }
            };
            case NOTEQUAL: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return !val.equals(attr.get(t));
                }

                @Override
                public String toString() {
                    return attr.field() + ' ' + express.value() + ' ' + val;
                }
            };
            case GREATERTHAN: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return ((Number) attr.get(t)).longValue() > ((Number) val).longValue();
                }

                @Override
                public String toString() {
                    return attr.field() + ' ' + express.value() + ' ' + val;
                }
            };
            case LESSTHAN: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return ((Number) attr.get(t)).longValue() < ((Number) val).longValue();
                }

                @Override
                public String toString() {
                    return attr.field() + ' ' + express.value() + ' ' + val;
                }
            };
            case GREATERTHANOREQUALTO: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return ((Number) attr.get(t)).longValue() >= ((Number) val).longValue();
                }

                @Override
                public String toString() {
                    return attr.field() + ' ' + express.value() + ' ' + val;
                }
            };
            case LESSTHANOREQUALTO: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return ((Number) attr.get(t)).longValue() <= ((Number) val).longValue();
                }

                @Override
                public String toString() {
                    return attr.field() + ' ' + express.value() + ' ' + val;
                }
            };
            case ISNULL: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return attr.get(t) == null;
                }

                @Override
                public String toString() {
                    return attr.field() + " = null";
                }
            };
            case ISNOTNULL: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return attr.get(t) != null;
                }

                @Override
                public String toString() {
                    return attr.field() + " != null";
                }
            };
            case OPAND: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return (((Number) attr.get(t)).longValue() & ((Number) val).longValue()) > 0;
                }

                @Override
                public String toString() {
                    return attr.field() + " & " + val + " > 0";
                }
            };
            case OPOR: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return (((Number) attr.get(t)).longValue() | ((Number) val).longValue()) > 0;
                }

                @Override
                public String toString() {
                    return attr.field() + " | " + val + " > 0";
                }
            };
            case OPANDNO: return new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return (((Number) attr.get(t)).longValue() & ((Number) val).longValue()) == 0;
                }

                @Override
                public String toString() {
                    return attr.field() + " & " + val + " = 0";
                }
            };
            case LIKE:
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs != null && rs.toString().contains(val.toString());
                    }

                    @Override
                    public String toString() {
                        return attr.field() + ' ' + express.value() + ' ' + val;
                    }
                };
            case NOTLIKE:
                return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Object rs = attr.get(t);
                        return rs == null || !rs.toString().contains(val.toString());
                    }

                    @Override
                    public String toString() {
                        return attr.field() + ' ' + express.value() + ' ' + val;
                    }
                };
            case BETWEEN:
            case NOTBETWEEN:
                Range range = (Range) val;
                final Comparable min = range.getMin();
                final Comparable max = range.getMax();
                if (express == BETWEEN) return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Comparable rs = (Comparable) attr.get(t);
                        if (rs == null) return false;
                        if (min != null && min.compareTo(rs) >= 0) return false;
                        return !(max != null && max.compareTo(rs) <= 0);
                    }

                    @Override
                    public String toString() {
                        return attr.field() + " BETWEEN " + min + " AND " + max;
                    }
                };
                if (express == NOTBETWEEN) return new Predicate<T>() {

                    @Override
                    public boolean test(T t) {
                        Comparable rs = (Comparable) attr.get(t);
                        if (rs == null) return true;
                        if (min != null && min.compareTo(rs) >= 0) return true;
                        return (max != null && max.compareTo(rs) <= 0);
                    }

                    @Override
                    public String toString() {
                        return attr.field() + " NOT BETWEEN " + min + " AND " + max;
                    }
                };
                return null;
            case IN:
            case NOTIN:
                Predicate<T> filter;
                if (val instanceof Collection) {
                    Collection array = (Collection) val;
                    if (array.isEmpty()) return null;
                    filter = new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            Object rs = attr.get(t);
                            return rs != null && array.contains(rs);
                        }

                        @Override
                        public String toString() {
                            return attr.field() + ' ' + express.value() + ' ' + val;
                        }
                    };
                } else {
                    if (Array.getLength(val) < 1) return null;
                    Class type = val.getClass();
                    if (type == int[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) return false;
                                int k = (int) rs;
                                for (int v : (int[]) val) {
                                    if (v == k) return true;
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return attr.field() + ' ' + express.value() + ' ' + Arrays.toString((int[]) val);
                            }
                        };
                    } else if (type == short[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) return false;
                                short k = (short) rs;
                                for (short v : (short[]) val) {
                                    if (v == k) return true;
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return attr.field() + ' ' + express.value() + ' ' + Arrays.toString((short[]) val);
                            }
                        };
                    } else if (type == long[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) return false;
                                long k = (long) rs;
                                for (long v : (long[]) val) {
                                    if (v == k) return true;
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return attr.field() + ' ' + express.value() + ' ' + Arrays.toString((long[]) val);
                            }
                        };
                    } else if (type == float[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) return false;
                                float k = (float) rs;
                                for (float v : (float[]) val) {
                                    if (v == k) return true;
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return attr.field() + ' ' + express.value() + ' ' + Arrays.toString((float[]) val);
                            }
                        };
                    } else if (type == double[].class) {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) return false;
                                double k = (double) rs;
                                for (double v : (double[]) val) {
                                    if (v == k) return true;
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return attr.field() + ' ' + express.value() + ' ' + Arrays.toString((double[]) val);
                            }
                        };
                    } else {
                        filter = new Predicate<T>() {

                            @Override
                            public boolean test(T t) {
                                Object rs = attr.get(t);
                                if (rs == null) return false;
                                for (Object v : (Object[]) val) {
                                    if (rs.equals(v)) return true;
                                }
                                return false;
                            }

                            @Override
                            public String toString() {
                                return attr.field() + ' ' + express.value() + ' ' + Arrays.toString((Object[]) val);
                            }
                        };
                    }
                }
                if (express == NOTIN) {
                    final Predicate<T> filter2 = filter;
                    filter = new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            return !filter2.test(t);
                        }

                        @Override
                        public String toString() {
                            return filter2.toString();
                        }
                    };
                }
                return filter;
        }
        return null;
    }

    protected static <E> Comparator<E> createFilterComparator(EntityInfo<E> info, Flipper flipper) {
        if (flipper == null || flipper.getSort() == null || flipper.getSort().isEmpty()) return null;
        Comparator<E> comparator = null;
        for (String item : flipper.getSort().split(",")) {
            if (item.trim().isEmpty()) continue;
            String[] sub = item.trim().split("\\s+");
            int pos = sub[0].indexOf('(');
            Attribute<E, Serializable> attr;
            if (pos <= 0) {
                attr = info.getAttribute(sub[0]);
            } else {  //含SQL函数
                int pos2 = sub[0].lastIndexOf(')');
                final Attribute<E, Serializable> pattr = info.getAttribute(sub[0].substring(pos + 1, pos2));
                final String func = sub[0].substring(0, pos);
                if ("ABS".equalsIgnoreCase(func)) {
                    if (pattr.type() == int.class || pattr.type() == Integer.class) {
                        attr = new Attribute<E, Serializable>() {

                            @Override
                            public Class type() {
                                return pattr.type();
                            }

                            @Override
                            public Class declaringClass() {
                                return pattr.declaringClass();
                            }

                            @Override
                            public String field() {
                                return pattr.field();
                            }

                            @Override
                            public Serializable get(E obj) {
                                return Math.abs(((Number) pattr.get(obj)).intValue());
                            }

                            @Override
                            public void set(E obj, Serializable value) {
                                pattr.set(obj, value);
                            }
                        };
                    } else if (pattr.type() == long.class || pattr.type() == Long.class) {
                        attr = new Attribute<E, Serializable>() {

                            @Override
                            public Class type() {
                                return pattr.type();
                            }

                            @Override
                            public Class declaringClass() {
                                return pattr.declaringClass();
                            }

                            @Override
                            public String field() {
                                return pattr.field();
                            }

                            @Override
                            public Serializable get(E obj) {
                                return Math.abs(((Number) pattr.get(obj)).longValue());
                            }

                            @Override
                            public void set(E obj, Serializable value) {
                                pattr.set(obj, value);
                            }
                        };
                    } else if (pattr.type() == float.class || pattr.type() == Float.class) {
                        attr = new Attribute<E, Serializable>() {

                            @Override
                            public Class type() {
                                return pattr.type();
                            }

                            @Override
                            public Class declaringClass() {
                                return pattr.declaringClass();
                            }

                            @Override
                            public String field() {
                                return pattr.field();
                            }

                            @Override
                            public Serializable get(E obj) {
                                return Math.abs(((Number) pattr.get(obj)).floatValue());
                            }

                            @Override
                            public void set(E obj, Serializable value) {
                                pattr.set(obj, value);
                            }
                        };
                    } else if (pattr.type() == double.class || pattr.type() == Double.class) {
                        attr = new Attribute<E, Serializable>() {

                            @Override
                            public Class type() {
                                return pattr.type();
                            }

                            @Override
                            public Class declaringClass() {
                                return pattr.declaringClass();
                            }

                            @Override
                            public String field() {
                                return pattr.field();
                            }

                            @Override
                            public Serializable get(E obj) {
                                return Math.abs(((Number) pattr.get(obj)).doubleValue());
                            }

                            @Override
                            public void set(E obj, Serializable value) {
                                pattr.set(obj, value);
                            }
                        };
                    } else {
                        throw new RuntimeException("Flipper not supported sort illegal type by ABS (" + flipper.getSort() + ")");
                    }
                } else if (func.isEmpty()) {
                    attr = pattr;
                } else {
                    throw new RuntimeException("Flipper not supported sort illegal function (" + flipper.getSort() + ")");
                }
            }
            Comparator<E> c = (E o1, E o2) -> {
                Comparable c1 = (Comparable) attr.get(o1);
                Comparable c2 = (Comparable) attr.get(o2);
                return c1 == null ? -1 : c1.compareTo(c2);
            };
            if (sub.length > 1 && sub[1].equalsIgnoreCase("DESC")) {
                c = c.reversed();
            }
            if (comparator == null) {
                comparator = c;
            } else {
                comparator = comparator.thenComparing(c);
            }
        }
        return comparator;
    }

    protected StringBuilder formatValue(Object value) {
        return formatValue(likefit, express, value);
    }

    protected static String formatToString(Object value) {
        StringBuilder sb = formatValue(true, null, value);
        return sb == null ? null : sb.toString();
    }

    private static StringBuilder formatValue(boolean likefit, FilterExpress express, Object value) {
        if (value == null) return null;
        if (value instanceof Number) return new StringBuilder().append(value);
        if (value instanceof CharSequence) {
            if (likefit && (express == LIKE || express == NOTLIKE)) value = "%" + value + '%';
            return new StringBuilder().append('\'').append(value.toString().replace("'", "\\'")).append('\'');
        } else if (value instanceof Range) {
            Range range = (Range) value;
            boolean rangestring = range.getClass() == Range.StringRange.class;
            StringBuilder sb = new StringBuilder();
            if (rangestring) {
                sb.append('\'').append(range.getMin().toString().replace("'", "\\'")).append('\'');
            } else {
                sb.append(range.getMin());
            }
            sb.append(" AND ");
            if (rangestring) {
                sb.append('\'').append(range.getMax().toString().replace("'", "\\'")).append('\'');
            } else {
                sb.append(range.getMax());
            }
            return sb;
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) return null;
            if (len == 1) {
                Object firstval = Array.get(value, 0);
                if (firstval != null && firstval.getClass().isArray()) return formatValue(likefit, express, firstval);
            }
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (int i = 0; i < len; i++) {
                Object o = Array.get(value, i);
                if (sb.length() > 1) sb.append(',');
                if (o instanceof CharSequence) {
                    sb.append('\'').append(o.toString().replace("'", "\\'")).append('\'');
                } else {
                    sb.append(o);
                }
            }
            return sb.append(')');
        } else if (value instanceof Collection) {
            Collection c = (Collection) value;
            if (c.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (Object o : c) {
                if (sb.length() > 1) sb.append(',');
                if (o instanceof CharSequence) {
                    sb.append('\'').append(o.toString().replace("'", "\\'")).append('\'');
                } else {
                    sb.append(o);
                }
            }
            return sb.append(')');
        }
        return new StringBuilder().append(value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (nodes == null) {
            sb.append(column).append(' ').append(express.value()).append(' ').append(formatValue(value));
        } else {
            if (column != null) sb.append('(').append(column).append(' ').append(express.value()).append(' ').append(formatValue(value));
            for (FilterNode node : this.nodes) {
                if (sb.length() > 0) sb.append(signand ? " AND " : " OR ");
                sb.append(node.toString());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    public Serializable getValue() {
        return value;
    }

    public void setValue(Serializable value) {
        this.value = value;
    }

    public boolean isSignand() {
        return signand;
    }

    public void setSignand(boolean signand) {
        this.signand = signand;
    }

    public String getTabalis() {
        return tabalis;
    }

    public void setTabalis(String tabalis) {
        this.tabalis = tabalis;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public FilterExpress getExpress() {
        return express;
    }

    public void setExpress(FilterExpress express) {
        this.express = express;
    }

    public FilterNode[] getNodes() {
        return nodes;
    }

    public void setNodes(FilterNode[] nodes) {
        this.nodes = nodes;
    }

}
