import postgres from "postgres";
import slugify from "slugify";

const databaseUrl =
  process.env.DATABASE_URL ??
  "postgresql://skills:skills_dev@localhost:5432/skills_graph";

const sql = postgres(databaseUrl, { max: 1 });

const LOCALES = [
  { locale: "en", display_name: "English", is_active: true },
  { locale: "vi", display_name: "Tiếng Việt", is_active: true },
  { locale: "fr", display_name: "Français", is_active: false },
  { locale: "ja", display_name: "日本語", is_active: false },
  { locale: "zh", display_name: "中文", is_active: false },
];

const ROOT_CATEGORIES = [
  {
    name: "Technology",
    category: "domain",
    path: "technology",
    external_id: "SK-0001",
  },
  {
    name: "Business",
    category: "domain",
    path: "business",
    external_id: "SK-0002",
  },
  {
    name: "Design",
    category: "domain",
    path: "design",
    external_id: "SK-0003",
  },
  {
    name: "Science",
    category: "domain",
    path: "science",
    external_id: "SK-0004",
  },
  {
    name: "Languages",
    category: "language",
    path: "languages",
    external_id: "SK-0005",
  },
  {
    name: "Soft Skills",
    category: "soft_skill",
    path: "soft_skills",
    external_id: "SK-0006",
  },
];

async function seed() {
  // Seed locales (idempotent via ON CONFLICT)
  for (const loc of LOCALES) {
    await sql`
      INSERT INTO locale_config (locale, display_name, is_active, coverage_pct)
      VALUES (${loc.locale}, ${loc.display_name}, ${loc.is_active}, 0)
      ON CONFLICT (locale) DO NOTHING
    `;
  }
  console.log(`Seeded ${LOCALES.length} locales`);

  // Seed root skill categories (idempotent via ON CONFLICT on external_id)
  for (const cat of ROOT_CATEGORIES) {
    const slug = slugify(cat.name, { lower: true, strict: true });
    await sql`
      INSERT INTO skills (external_id, canonical_name, slug, status, category, path, source)
      VALUES (${cat.external_id}, ${cat.name}, ${slug}, 'active', ${cat.category}, ${cat.path}::ltree, 'curator')
      ON CONFLICT (external_id) DO NOTHING
    `;
  }
  console.log(`Seeded ${ROOT_CATEGORIES.length} root categories`);

  await sql.end();
}

seed().catch((err) => {
  console.error("Seed failed:", err);
  process.exit(1);
});
