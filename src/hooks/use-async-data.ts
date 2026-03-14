import { useCallback, useEffect, useState, type DependencyList } from "react";

interface UseAsyncDataOptions<T> {
  initialData: T;
  fetcher: () => Promise<T>;
  deps?: DependencyList;
}

interface UseAsyncDataResult<T> {
  data: T;
  loading: boolean;
  error: Error | null;
  reload: () => Promise<void>;
}

function normalizeError(value: unknown): Error {
  return value instanceof Error ? value : new Error(String(value));
}

export function useAsyncData<T>(options: UseAsyncDataOptions<T>): UseAsyncDataResult<T> {
  const { initialData, fetcher, deps = [] } = options;
  const [data, setData] = useState<T>(initialData);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const reload = useCallback(async (): Promise<void> => {
    setLoading(true);
    setError(null);

    try {
      const nextData = await fetcher();
      setData(nextData);
    } catch (fetchError) {
      setError(normalizeError(fetchError));
    } finally {
      setLoading(false);
    }
  }, [fetcher]);

  useEffect(() => {
    void reload();
  }, [reload, ...deps]);

  return { data, loading, error, reload };
}
