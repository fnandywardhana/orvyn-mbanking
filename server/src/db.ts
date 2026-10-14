import postgres from 'postgres'
import { readdir, readFile } from 'node:fs/promises'

export type Sql = postgres.Sql

export function connect(url = process.env.DATABASE_URL ?? 'postgres://orvyn:orvyn@127.0.0.1:55432/orvyn') {
  return postgres(url, { onnotice: () => {} })
}

// Applies migrations/NNN_*.sql in order, once each.
export async function migrate(sql: Sql) {
  await sql`create table if not exists schema_migrations (name text primary key, applied_at timestamptz not null default now())`
  const dir = new URL('../migrations/', import.meta.url)
  const done = new Set((await sql`select name from schema_migrations`).map(r => r.name))
  for (const name of (await readdir(dir)).filter(f => f.endsWith('.sql')).sort()) {
    if (done.has(name)) continue
    await sql.begin(async tx => {
      await tx.unsafe(await readFile(new URL(name, dir), 'utf8'))
      await tx`insert into schema_migrations (name) values (${name})`
    })
  }
}
