/** Convert a JS number array to PostgreSQL vector literal. */
export function toSql(embedding: number[]): string {
  return `[${embedding.join(",")}]`;
}

/** Parse a PostgreSQL vector literal back to a JS number array. */
export function fromSql(pgString: string): number[] {
  return pgString
    .slice(1, -1)
    .split(",")
    .map(Number);
}
