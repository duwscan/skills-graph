import postgres from "postgres";
import path from "node:path";
import fs from "node:fs";

const databaseUrl =
  process.env.DATABASE_URL ??
  "postgresql://skills:skills_dev@localhost:5432/skills_graph";

const sql = postgres(databaseUrl, { max: 1 });

const MIGRATIONS_DIR = path.join(import.meta.dir, "migrations");

async function migrate() {
  // Create migrations tracking table
  await sql`
    CREATE TABLE IF NOT EXISTS _migrations (
      id SERIAL PRIMARY KEY,
      name TEXT UNIQUE NOT NULL,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `;

  // Get already-applied migrations
  const applied = await sql`SELECT name FROM _migrations ORDER BY id`;
  const appliedNames = new Set(applied.map((r) => r.name));

  // Read migration files sorted by name
  const files = fs
    .readdirSync(MIGRATIONS_DIR)
    .filter((f) => f.endsWith(".sql"))
    .sort();

  let count = 0;
  for (const file of files) {
    if (appliedNames.has(file)) {
      console.log(`  ⏭  ${file} (already applied)`);
      continue;
    }

    const filePath = path.join(MIGRATIONS_DIR, file);
    const migration = fs.readFileSync(filePath, "utf-8");

    console.log(`  ▶  Applying ${file}...`);
    await sql.unsafe(migration);
    await sql`INSERT INTO _migrations (name) VALUES (${file})`;
    console.log(`  ✓  Migration ${file} applied`);
    count++;
  }

  if (count === 0) {
    console.log("All migrations already applied.");
  } else {
    console.log(`\n${count} migration(s) applied successfully.`);
  }

  await sql.end();
}

migrate().catch((err) => {
  console.error("Migration failed:", err);
  process.exit(1);
});
