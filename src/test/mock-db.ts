export type MockDb = {
  execAsync: jest.Mock<Promise<void>, [string]>;
  runAsync: jest.Mock<Promise<unknown>, [string, unknown[]?]>;
  getAllAsync: jest.Mock<Promise<unknown[]>, [string, unknown[]?]>;
  getFirstAsync: jest.Mock<Promise<unknown>, [string, unknown[]?]>;
  withTransactionAsync: jest.Mock<Promise<void>, [() => Promise<void>]>;
};

export function createMockDb(): MockDb {
  return {
    execAsync: jest.fn(async (_sql: string) => undefined),
    runAsync: jest.fn(async (_sql: string, _params?: unknown[]) => ({ changes: 1 })),
    getAllAsync: jest.fn(async (_sql: string, _params?: unknown[]) => []),
    getFirstAsync: jest.fn(async (_sql: string, _params?: unknown[]) => null),
    withTransactionAsync: jest.fn(async (callback: () => Promise<void>) => {
      await callback();
    }),
  };
}
