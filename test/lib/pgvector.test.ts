import { describe, it, expect } from "bun:test";
import { toSql, fromSql } from "../../src/lib/pgvector";

describe("pgvector helpers", () => {
  describe("toSql", () => {
    it("converts a number array to PostgreSQL vector literal", () => {
      expect(toSql([0.1, 0.2, 0.3])).toBe("[0.1,0.2,0.3]");
    });

    it("handles a single element", () => {
      expect(toSql([1.0])).toBe("[1]");
    });

    it("handles an empty array", () => {
      expect(toSql([])).toBe("[]");
    });

    it("handles large dimensions", () => {
      const vec = Array.from({ length: 1024 }, (_, i) => i / 1024);
      const result = toSql(vec);
      expect(result.startsWith("[")).toBe(true);
      expect(result.endsWith("]")).toBe(true);
      expect(result.split(",").length).toBe(1024);
    });

    it("handles negative values", () => {
      expect(toSql([-0.5, 0, 0.5])).toBe("[-0.5,0,0.5]");
    });
  });

  describe("fromSql", () => {
    it("parses a PostgreSQL vector literal to number array", () => {
      expect(fromSql("[0.1,0.2,0.3]")).toEqual([0.1, 0.2, 0.3]);
    });

    it("handles a single element", () => {
      expect(fromSql("[1]")).toEqual([1]);
    });

    it("handles negative values", () => {
      expect(fromSql("[-0.5,0,0.5]")).toEqual([-0.5, 0, 0.5]);
    });
  });

  describe("round-trip", () => {
    it("toSql -> fromSql preserves values", () => {
      const original = [0.123, -0.456, 0.789, 0, 1];
      expect(fromSql(toSql(original))).toEqual(original);
    });
  });
});
