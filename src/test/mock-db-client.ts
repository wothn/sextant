export const mockGetDb = jest.fn();

export const mockDbClientModule = {
  getDb: () => mockGetDb(),
};

export function resetMockDbClient(): void {
  jest.restoreAllMocks();
  mockGetDb.mockReset();
}
