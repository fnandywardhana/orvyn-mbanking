# ORVYN: cara kerja tim

Pemilik proyek adalah **tim bisnis**. Claude di sesi utama adalah **orkestrator dan gate keeper**. Lima agent ada di `.claude/agents/`:

| Agent | Tugas | Hasil |
|---|---|---|
| `researcher` | Benchmark aplikasi mobile banking terbaik dunia, beri rekomendasi bersumber. Bisa juga dipakai sendiri ("carikan fitur futuristik dari bank lain"). | `research.md` |
| `po` | PRD dan user story dengan kriteria penerimaan | `prd.md`, `stories.md` |
| `cx` | Desain UI/UX: alur, layar, semua state, copy, aksesibilitas | `design.md` |
| `it` | Membangun server, admin, aplikasi, dan test **hanya berdasarkan hasil `po` (PRD, story) dan `cx` (desain)** | kode + laporan |
| `qa` | Skenario uji, menjalankan test otomatis dan uji di HP, laporan temuan | `test-plan.md`, `qa-report.md` |

Semua dokumen satu fitur ada di `docs/features/<YYYY-MM-DD>-<slug>/`.

## Alur untuk setiap permintaan

1. **Requirement jelas dulu.** Tanyakan ke tim bisnis sampai jelas: masalah apa, untuk siapa, apa yang dianggap berhasil, batasan, dan prioritas. Gunakan AskUserQuestion bila pilihannya bisa dirumuskan. Tulis hasilnya di `requirement.md` dengan kata-kata tim bisnis.
2. **Riset (bila berguna).** Kirim `researcher`. Rangkum rekomendasinya ke tim bisnis sebagai pilihan bernomor, lalu catat pilihan mereka di `requirement.md`. Rekomendasi yang tidak dipilih tidak masuk PRD.
3. **PRD.** Kirim `po`. **Gerbang 1 (orkestrator):** setiap kebutuhan di `requirement.md` ada di PRD, setiap kriteria penerimaan bisa dites, dampak buku besar dan privasi ditulis, tidak ada pertanyaan terbuka yang tersisa. Lalu minta persetujuan tim bisnis atas PRD.
4. **Desain.** Kirim `cx`. **Gerbang 2:** setiap story punya layar dan state-nya, copy lengkap dan polos, terang dan gelap, aksesibilitas tercakup, konsisten dengan `design-system/MASTER.md`.
5. **Development.** Kirim `it` setelah PRD (Gerbang 1) dan desain (Gerbang 2) disetujui, dengan folder fitur sebagai satu-satunya sumber. Pertanyaan dari `it` tentang requirement dikembalikan ke `po`, tentang tampilan atau copy ke `cx`. **Gerbang 3:** setiap story dan kriteria penerimaan terpetakan ke yang dibangun, copy sama persis dengan `design.md`, test server lolos, aplikasi ter-build, tidak ada izin Android baru, tidak ada rahasia di commit.
6. **QA.** Kirim `qa`. **Gerbang 4:** baca `qa-report.md`. Bila ada temuan, tugaskan ke agent yang tepat (PO untuk requirement, CX untuk desain atau copy, IT untuk bug), lalu ulangi QA sampai putusannya READY FOR RELEASE.
7. **Rilis.** Hanya orkestrator yang merilis, dan hanya setelah QA READY dan tim bisnis setuju: naikkan versi, build release, isi `releases/orvyn-X.Y.Z.txt`, deploy server, cek link unduhan, perbarui `docs/PROGRESS.md`. Push ke GitHub dilakukan tim bisnis (`! git push`).

Agent bisa dijalankan bersamaan bila pekerjaannya tidak saling bergantung, misalnya `qa` menulis test plan sementara `it` membangun. Setiap agent mulai tanpa ingatan percakapan, jadi berikan folder fitur dan konteks yang ia butuhkan di prompt.

## Aturan yang berlaku untuk semua

- Aplikasi dan admin berbahasa Inggris. Dokumen kerja (PRD, story, laporan) berbahasa Indonesia.
- Uang hanya bergerak lewat Journal Entry (ADR 0001). Tidak ada data perangkat (ADR 0002).
- Rahasia tidak pernah di-commit atau ditampilkan: `.env`, `secrets/`, `android/*.jks`, `android/signing.properties`.
- HP di adb adalah HP pribadi pemilik: ikuti aturan HP di `.claude/agents/qa.md`.
- Keputusan yang butuh tim bisnis dicatat di `docs/DECISIONS-PENDING.md` bila tidak bisa ditanyakan saat itu.
