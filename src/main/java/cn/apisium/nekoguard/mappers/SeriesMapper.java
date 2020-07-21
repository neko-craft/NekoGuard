package cn.apisium.nekoguard.mappers;

import org.influxdb.dto.QueryResult;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class SeriesMapper {
    private final HashMap<String, Integer> map = new HashMap<>();
    private final int size;
    private final String[] columns;

    public SeriesMapper(@NotNull final String ...keys) {
        for (int i = 0; i < keys.length; i++) map.put(keys[i], i);
        size = keys.length;
        columns = keys;
    }

    @SuppressWarnings("unused")
    @NotNull
    public String[] getColumns() { return columns; }

    @NotNull
    public final Mapper parse(@NotNull final QueryResult.Series data) {
        return new Mapper(data);
    }

    public final class Mapper {
        private final QueryResult.Series data;
        private final int[] map2;
        public final int count;
        protected Mapper(@NotNull final QueryResult.Series data) {
            this.data = data;
            count = data.getValues().size();
            map2 = new int[size];
            final List<String> list = data.getColumns();
            int i = list.size();
            while (i-- != 0) {
                final Integer key = map.get(list.get(i));
                if (key != null) map2[i] = key;
            }
        }

        @NotNull
        public Object[] get(final int index) {
            final List<?> list = data.getValues().get(index);
            int i = list.size();
            final Object[] arr = new Object[size];
            while (i-- != 0) arr[map2[i]] = list.get(i);
            return arr;
        }

        @NotNull
        public Object[][] allArray() {
            int i = count;
            final Object[][] arr = new Object[i][size];
            while (i-- != 0) arr[i] = get(i);
            return arr;
        }

        @NotNull
        public List<Object[]> all() {
            return Arrays.asList(allArray());
        }
    }
}
