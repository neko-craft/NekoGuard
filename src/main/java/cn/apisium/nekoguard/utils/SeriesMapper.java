package cn.apisium.nekoguard.utils;

import org.influxdb.dto.QueryResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class SeriesMapper {
    private final HashMap<String, Integer> map = new HashMap<>();
    private final int size;
    private final String[] columns;

    public SeriesMapper(final String ...keys) {
        for (int i = 0; i < keys.length; i++) map.put(keys[i], i);
        size = keys.length;
        columns = keys;
    }
    public String[] getColumns() { return columns; }

    public final Mapper parse(final QueryResult.Series data) {
        return new Mapper(data);
    }

    public final class Mapper {
        private final QueryResult.Series data;
        private final int[] map2;
        protected Mapper(final QueryResult.Series data) {
            this.data = data;
            map2 = new int[size];
            final List<String> list = data.getColumns();
            int i = list.size();
            while (i-- != 0) {
                final Integer key = map.get(list.get(i));
                if (key != null) map2[i] = key;
            }
        }

        public Object[] get(final int index) {
            final List<?> list = data.getValues().get(index);
            int i = list.size();
            final Object[] arr = new Object[size];
            while (i-- != 0) arr[map2[i]] = list.get(i);
            return arr;
        }

        public Object[][] allArray() {
            int i = data.getValues().size();
            final Object[][] arr = new Object[i][size];
            while (i-- != 0) arr[i] = get(i);
            return arr;
        }

        public List<Object[]> all() {
            return Arrays.asList(allArray());
        }
    }
}
