# Desain: Quality pass 0.9.0

Penulis: CX, 17 Okt 2026. Sumber: `requirement.md`, `prd.md` (keputusan PO-1..PO-27, bagian 7 dan 8), `stories.md` (Q-A1..Q-G11), `ui-audit.md`, `e2e-report.md` dengan screenshot di `qa-shots/` dan render 200% di `qa-shots/robo/`, `design-system/MASTER.md`, kode `android/app/src/main/java/id/orvyn/app/ui/` dan halaman admin di `server/src/admin-*.ts`.

**Cara dokumen ini dibuat.** HP pemilik tidak dipakai. Desain dibuat dari kode, screenshot QA, dan render Robolectric 200%. Skill yang dipakai: `ui-ux-pro-max` (prioritas aksesibilitas, sentuhan, tata letak, gerak, form), `dataviz` (grafik Insights dan Fund, warna batang divalidasi dengan `validate_palette.js`), dan `humanizer` (semua copy baru). Rasio kontras dihitung dengan rumus WCAG 2.x (hasil di bagian 3.3).

**Cara membaca.** Komponen bersama dijelaskan sekali di bagian 4. Bagian story (5 sampai 11) hanya menulis yang khusus untuk story itu dan merujuk ke bagian 4. Copy dalam `kode` adalah teks final bahasa Inggris. IT menyalinnya persis, termasuk tanda baca. `{kurung kurawal}` adalah nilai yang diisi. Contoh nama, nomor rekening, dan nomor HP di dokumen ini fiktif (persona demo dan contoh `0812 3456 7890` yang sudah dipakai di aplikasi).

**Istilah ukuran.** "Font besar" berarti `fontScale >= 1.5`. "Layar pendek" berarti tinggi jendela di bawah 700dp. "Uji 200%" sama dengan di `stories.md`: 360×640dp dan 411×869dp, terang dan gelap.

---

## 1. Ringkasan keputusan CX

1. **Tiga pola layar bersama** menutup blocker E2E-01: layar PIN (4.6), layar nominal (4.7), dan layar sukses (4.8). Ketiganya punya area atas yang bisa digulir dan area aksi yang menempel di bawah.
2. **Hanya dua hal yang boleh tidak ikut membesar di atas 1,5×:** angka keypad dan label bottom nav. Semua teks lain ikut membesar penuh. Keduanya tetap dibaca TalkBack dengan label penuh.
3. **Satu aturan kegagalan:** setiap aksi yang gagal memberi pesan di tempat yang paling dekat dengan aksinya (field, sheet, kotak di atas tombol, atau snackbar), dan setiap layar yang memuat data punya "Try again" (4.10 sampai 4.12).
4. **PIN pad punya keadaan sibuk** dengan teks status di slot tetap. Goyangan dan getar REJECT hanya untuk PIN yang ditolak (salah, lemah, tidak cocok), tidak pernah untuk saldo kurang atau offline (4.6).
5. **Chip satu gaya di dua tema, dengan centang**, dan segmented button untuk dua pilihan yang saling eksklusif (4.9).
6. **Tidak ada izin Android baru.** Senter memakai izin kamera yang sudah ada. Tidak ada data HP yang dikirim.

---

## 2. Tabel jejak: story → bagian desain

| Story | Judul singkat | Bagian di dokumen ini | Komponen bersama |
|---|---|---|---|
| Q-A1 | Layar PIN tahan font besar | 5.1 | 4.6, 4.1 |
| Q-A2 | Layar nominal tahan font besar | 5.2 | 4.7, 4.5, 4.4 |
| Q-A3 | Layar sukses tahan font besar | 5.3 | 4.8 |
| Q-A4 | Nominal tidak patah di tengah angka | 5.4 | 4.5 |
| Q-A5 | Beranda dan navigasi tahan font besar | 5.5 | 4.1 |
| Q-A6 | Baris daftar dan tombol tahan font besar | 5.6 | 4.14, 4.4 |
| Q-B1 | Aksi yang gagal selalu memberi tahu | 6.1 | 4.11, 4.12 |
| Q-B2 | Sukses pencairan deposito lebih awal | 6.2 | 4.8, 4.6 |
| Q-B3 | Layar gagal memuat punya "Try again" | 6.3 | 4.10 |
| Q-B4 | Layar periksa Transfer pulih setelah offline | 6.4 | 4.10, 4.12 |
| Q-B5 | PIN pad memberi tahu saat diproses | 6.5 | 4.6 |
| Q-B6 | Beranda offline yang jujur | 6.6 | 4.13 |
| Q-B7 | Tarik untuk refresh memperbarui seluruh beranda | 6.7 | 4.13 |
| Q-B8 | Maintenance langsung mengganti layar | 6.8 | 4.10 |
| Q-B9 | Layar terkunci bisa dicoba lagi | 6.9 | 4.6 |
| Q-B10 | E-statement: satu PDF gagal tidak menghapus daftar | 6.10 | 4.11 |
| Q-C1 | Ganti PIN memeriksa PIN lama dulu | 7.1 | 4.6 |
| Q-C2 | Ganti password menghitung percobaan | 7.2 | 4.12 |
| Q-C3 | Reset demo berhasil walau ada passkey | 7.3, 12.3 | |
| Q-C4 | PIN pad tidak bisa direkam layar | 7.4 | 4.6 |
| Q-C5 | Aksi destruktif selalu dikonfirmasi | 7.5 | 4.16 |
| Q-C6 | Status Freeze dan sakelar kartu yang pasti | 7.6 | 4.15, 4.11 |
| Q-C7 | Server menolak penolakan tanpa alasan | 7.7, 12.1 | |
| Q-C8 | Password awal petugas tidak lewat URL | 7.8, 12.2 | |
| Q-D1 | Sign in: password dan pesan terlihat | 8.1 | 4.4 |
| Q-D2 | About you: kesalahan terlihat, cek NIK | 8.2 | 4.12 |
| Q-D3 | Create PIN: PIN lemah dan tidak cocok | 8.3 | 4.6 |
| Q-D4 | Kode OTP terlihat bila notifikasi mati | 8.4 | |
| Q-D5 | Selfie punya panah kembali | 8.5 | |
| Q-D6 | "Forgot PIN?" di Unlock | 8.6 | 4.6 |
| Q-E1 | Chip pilihan yang jelas di dua tema | 9.1 | 4.9 |
| Q-E2 | Satu huruf dan kontras AA | 9.2 | 4.1, 4.2 |
| Q-E3 | Transisi layar yang memberi arah | 9.3 | 4.3 |
| Q-E4 | Ketukan ganda tidak terbawa | 9.4 | 4.3, 4.4 |
| Q-E5 | Semua sakelar membacakan statusnya | 9.5 | 4.15 |
| Q-E6 | Vyna untuk TalkBack, jawaban rapi | 9.6 | |
| Q-E7 | Label TalkBack dan tanda belum dibaca inbox | 9.7 | |
| Q-E8 | Baris yang bisa diketuk selalu membuka sesuatu | 9.8 | 4.14 |
| Q-E9 | Status permintaan uang dengan ikon dan kata | 9.9 | 4.17 |
| Q-E10 | Ikon tagihan per jenis | 9.10 | |
| Q-E11 | Grafik Insights dan kategori kosong | 9.11 | 4.2 |
| Q-E12 | Skeleton tenang, tanpa strip status bar | 9.12 | 4.10 |
| Q-E13 | Kartu satu gaya, nama panjang di bukti | 9.13 | |
| Q-F1 | Detail rekening (S1.9) | 10.1 | 4.10, 4.13 |
| Q-F2 | "Share receipt" dan tanggal di sukses | 10.2 | 4.8 |
| Q-F3 | Filter riwayat Date dan Type | 10.3 | 4.9, 4.10 |
| Q-F4 | Nominal opsional di QR-ku | 10.4 | 4.7 |
| Q-F5 | Pulsa: nomor sendiri, operator saat mengetik | 10.5 | |
| Q-F6 | Emas dalam gram, unit di detail | 10.6 | 4.7, 4.9 |
| Q-F7 | Bayar permintaan lewat layar periksa | 10.7 | 4.6, 4.8, 4.16 |
| Q-F8 | Admin: filter jenis, banner, Settings | 10.8, 12.4, 12.5 | |
| Q-G1 | Banner promo di bawah transaksi terakhir | 11.1 | |
| Q-G2 | Pemindai QRIS dengan senter | 11.2 | |
| Q-G3 | Bantuan "Ask Vyna", Privacy lengkap | 11.3 | |
| Q-G4 | Format angka, tanggal, kurs | 11.4 | |
| Q-G5 | Copy yang tepat untuk situasinya | 11.5 | |
| Q-G6 | Pencarian kosong, minimum di tombol | 11.6 | 4.10 |
| Q-G7 | Back dari periksa Vyna kembali ke Vyna | 11.7 | |
| Q-G8 | Kurs dari sisi nasabah, grafik Fund | 11.8 | 4.9 |
| Q-G9 | Tidak ada field teks di layar keypad | 11.9 | 4.7 |
| Q-G10 | Judul riwayat kantong | 11.10 | |
| Q-G11 | Copy widget | 11.11 | |

Semua 62 story tercakup. Admin ada di bagian 12, teks yang dikirim server di bagian 13, ikon baru di bagian 14.

---

## 3. Perubahan design system

### 3.1 Yang berubah di `design-system/MASTER.md`

| Bagian MASTER | Perubahan | Story |
|---|---|---|
| §1 Warna | `textTertiary` terang `#6B7690` → `#616D86`, tabel kontras diperbarui. Catatan nama token Compose (`outline` MASTER = `outlineVariant` Compose, `outlineStrong` MASTER = `outline` Compose). Token grafik `chart.barMuted`. | Q-E2, Q-E11, Q-E13 |
| §2 Tipografi | Skala 15 peran Material 3, semua Plus Jakarta Sans dan `tnum`. Batas skala font hanya untuk angka keypad dan label bottom nav. Nominal mengecil otomatis, membungkus hanya di koma ribuan. Uang tidak pernah dikapitalkan. | Q-E2, Q-A1, Q-A4, Q-A5, Q-G4 |
| §3 Ruang | Area aksi menempel. Pola layar dengan keypad. Tidak ada field teks di layar keypad. | Q-A1..Q-A3, Q-G9 |
| §4 Gerak | Spesifikasi transisi 250ms yang konkret, langkah dalam alur, tab, input ditahan selama transisi masuk. | Q-E3, Q-E4 |
| §5 Komponen | Button (tinggi minimal, sekali tembak, destruktif isi dan teks), Chip, Filter chip, Segmented button, Amount input (mengecil), PIN pad (sibuk, slot status, aman), List row (font besar), Switch row, Snackbar (durasi, ikon, posisi), Error state (Try again tonal, 15 detik), Banner status, Kotak error form, Success screen (menempel, Date, Share receipt), Dialog (konfirmasi destruktif terisi), Status chip permintaan uang. | 4.x |
| §6 Menulis | Pola "N tries left", periode "Oct 2026", kurs per 100 unit, aplikasi hanya bahasa Inggris. | Q-G4, Q-C1 |
| §8 Larangan | Field teks di layar keypad, kegagalan tanpa pesan, goyangan PIN untuk error yang bukan PIN. | Q-B1, Q-B5, Q-G9 |
| §9 Checklist | Uji 360×640dp pada 2,0, setiap aksi gagal terlihat, copy hanya bahasa Inggris. | semua |
| §5, §2 (rapi) | Contoh label yang masih bahasa Indonesia di MASTER ("Berhasil", "Bagikan bukti", "Selesai", "Coba lagi", "REKENING") diganti label bahasa Inggris yang dipakai aplikasi. §7 admin: `err-box`, rahasia tidak lewat URL, status dengan kata. | Q-C8, Q-F8 |

### 3.2 Yang tidak berubah

Warna merek, hero, garis jersey, Giallo hanya untuk Prestige, ikon Phosphor, aturan uang (keluar tidak merah, tanda minus asli, angka tabular), biaya sebelum PIN, label tombol yang menyebut nominal, jarak, radius, elevasi.

### 3.3 Kontras pasangan warna baru (WCAG 2.x, dihitung)

| Pasangan | Terang | Gelap | Syarat |
|---|---|---|---|
| `textTertiary` di `background` | 4.81 | 5.48 | 4.5 teks |
| `textTertiary` di `surfaceVariant` | 4.59 | 4.66 | 4.5 teks |
| `textTertiary` di `surface` | 5.20 | 5.09 | 4.5 teks |
| Garis chip `outlineStrong` di `background` | 3.01 | 3.56 | 3.0 objek |
| Garis chip `outlineStrong` di `surface` | 3.25 | 3.30 | 3.0 objek |
| Chip dipilih: `onPrimary` di `primary` | 10.69 | 6.54 | 4.5 teks |
| Chip dipilih: isi `primary` di `background` | 9.89 | 8.02 | 3.0 objek |
| Banner offline: `text` di `warningContainer` | 16.95 | 12.71 | 4.5 teks |
| Banner offline: `textSecondary` di `warningContainer` | 6.37 | 6.66 | 4.5 teks |
| Ikon banner offline: `warning` di `warningContainer` | 4.86 | 8.11 | 3.0 ikon |
| Status chip Waiting: `textSecondary` di `surfaceVariant` | 6.27 | 7.46 | 4.5 teks |
| Status chip To pay: `warning` di `warningContainer` | 4.86 | 8.11 | 4.5 teks |
| Status chip Paid: `success` di `successContainer` | 4.72 | 7.18 | 4.5 teks |
| Snackbar: teks di `inverseSurface` | 19.66 | 16.01 | 4.5 teks |
| Snackbar: aksi `inversePrimary` di `inverseSurface` | 7.97 | 9.04 | 4.5 teks |
| Tombol destruktif: `onError` di `error` | 5.62 | 7.12 | 4.5 teks |
| Garis tombol "Share receipt" (putih 56%) di hero | 4.33 (Nerazzurro), 6.48 (Nero) | sama | 3.0 objek |
| Chip error QRIS: `text` di putih / ikon `error` di putih | 18.93 / 5.62 | sama (selalu putih) | 4.5 / 3.0 |
| Batang Insights lain (`chart.barMuted`) di kartu | 3.50 (`#7088C4`) | 3.36 (`#55689C`) | 3.0 objek |
| Kotak info OTP: `onPrimaryContainer` di `primaryContainer` | 13.60 | 8.50 | 4.5 teks |

Semua lolos. Garis chip terang di `background` (3.01) pas di batas, jadi chip tidak boleh diletakkan di atas `surfaceVariant`. Di sana pakai `surface` sebagai latar baris.

---

## 4. Komponen bersama

### 4.1 Skala tipe 15 peran dan batas skala font

Semua peran memakai Plus Jakarta Sans dan `fontFeatureSettings = "tnum"`. Tidak ada peran yang jatuh ke Roboto, termasuk komponen Material (DatePicker, DateRangePicker, AlertDialog, Snackbar, Switch, SegmentedButton) karena semuanya membaca `MaterialTheme.typography`.

| Peran Material 3 | Ukuran / tinggi baris (sp) | Berat | Dipakai untuk |
|---|---|---|---|
| `displayLarge` | 44 / 52 | 800 | Tidak dipakai. Didefinisikan supaya tidak jatuh ke Roboto |
| `displayMedium` | 40 / 48 | 800 | Tidak dipakai. Sama |
| `displaySmall` | 34 / 40, tracking −0.5% | 800 | Sama dengan `displayBalance`. Nominal valas di layar tukar (Q-E2 AC3) |
| `headlineLarge` | 28 / 34 | 700 | Judul layar besar |
| `headlineMedium` | 24 / 30 | 700 | Judul layar |
| `headlineSmall` | 22 / 28 | 700 | Judul kantong, sapaan Vyna |
| `titleLarge` | 20 / 26 | 600 | Judul sheet dan seksi, judul PIN di font besar |
| `titleMedium` | 16 / 22 | 600 | Judul baris dan kartu |
| `titleSmall` | 14 / 20 | 600 | Nama kantong di kartu kecil, label nilai kecil |
| `bodyLarge` | 16 / 24 | 400 | Bacaan utama, field |
| `bodyMedium` | 14 / 20 | 400 | Subjudul baris, keterangan (persen aset, tenor, NAV), helper |
| `bodySmall` | 12 / 16 | 400 | **Hanya** caption di bawah grafik |
| `labelLarge` | 15 / 20 | 600 | Tombol |
| `labelMedium` | 12 / 16 | 600 | Chip, badge, label nav |
| `labelSmall` | 11 / 16, tracking +8% | 700 | Eyebrow (huruf kapital lewat kode) |

`OrvynType.displayBalance` tetap ada dan sama dengan `displaySmall`.

**Pindahan wajib (Q-E2 AC4):** semua pemakaian `bodySmall` untuk keterangan baris diganti `bodyMedium` (daftar di `ui-audit.md` UI-12, misalnya `Wealth.kt:110`, `Invest.kt:116-140`, `Deposits.kt:93-171`, `Fx.kt:76-105`, `HomeScreen.kt:311`, `RelationshipManagerCard.kt:44-45`). `bodySmall` tersisa hanya di caption grafik Insights dan Fund.

**Batas skala font.** Hanya dua tempat yang ukurannya berhenti di 1,5×:
- Angka dan simbol keypad (`1`..`9`, `0`, `000`, `.`): ukuran efektif `min(fontScale, 1.5) × ukuran dasar`. Ukuran tombol keypad tidak bergantung pada font.
- Label bottom nav (`Home`, `History`, `QRIS`, `Cards`, `Profile`).

Alasannya: keduanya berada di sel lebar tetap (sepertiga dan seperlima layar). Di 2,0 kata "Profile" di sel 72dp akan patah. Teks lain tidak boleh dibatasi.

**Uang tidak pernah dikapitalkan (Q-G4 AC3).** Eyebrow memakai `uppercase()` hanya pada kata. Nominal tidak pernah masuk eyebrow. Bila eyebrow butuh total, total ditulis di baris sendiri (contoh di 11.4).

### 4.2 Warna dan nama token

| Token MASTER | Terang | Gelap | Nama di `theme/Theme.kt` |
|---|---|---|---|
| `textTertiary` | **`#616D86`** (dulu `#6B7690`) | `#7C879E` (tetap) | `OrvynColors.textTertiary` |
| `outline` (garis kartu, pemisah) | `#DCE2EE` | `#212A3E` | `colorScheme.outlineVariant` |
| `outlineStrong` (garis input, chip) | `#858FA3` | `#5D6884` | `colorScheme.outline` |
| `chart.barMuted` (baru) | `#7088C4` | `#55689C` | `OrvynColors.chartBarMuted` |

Catatan untuk IT: nama token MASTER dan Compose tertukar untuk dua garis. Story Q-E13 menyebut `outlineVariant`, artinya garis kartu tipis MASTER `outline`. Tidak ada warna baru selain `chart.barMuted`.

### 4.3 Transisi dan penahan input

| Perpindahan | Masuk | Keluar | Kurva |
|---|---|---|---|
| Maju ke layar baru (NavHost `enterTransition`/`exitTransition`) | Layar baru geser dari kanan sejauh 1/4 lebar + fade in, 250ms | Layar lama geser ke kiri 1/4 lebar + fade out, 175ms | Masuk: emphasized decelerate `cubic(0.05, 0.7, 0.1, 1.0)`. Keluar: emphasized accelerate `cubic(0.3, 0.0, 0.8, 0.15)` |
| Kembali (`popEnter`/`popExit`) | Layar sebelumnya geser dari kiri 1/4 + fade in, 250ms | Layar sekarang geser ke kanan 1/4 + fade out, 175ms | Sama |
| Langkah di dalam alur (`AnimatedContent` pada `step`) | Sama dengan maju bila indeks langkah naik, sama dengan kembali bila turun | | Sama |
| Ganti tab bottom nav | Crossfade 150ms, tanpa geser (tab setara, bukan maju) | | Standard |
| Ke layar sukses | Fade in 250ms tanpa geser, lalu centang menggambar 400ms | | Decelerate |
| Sheet dan dialog | Bawaan Material 3 | | |

- **Animasi mati** (animator scale 0): `EnterTransition.None` dan `ExitTransition.None`, layar berganti langsung (Q-E3 AC3).
- **Predictive back**: `android:enableOnBackInvokedCallback="true"` di manifest. Gestur kembali memakai animasi pop di atas. Back sistem tetap mundur satu langkah di setiap alur (Q-E3 AC4). Selama PIN sibuk (4.6), Back diabaikan.
- **Tidak ada fade 700ms** di mana pun.

**Penahan input (Q-E4).** Dua aturan, dua-duanya wajib:
1. **Tombol sekali tembak.** `OrvynButton`, `OrvynTextButton`, baris yang membuka layar, dan chip yang memajukan alur mengabaikan ketukan berikutnya selama 600ms setelah ketukan pertama, atau sampai layar berganti, mana yang lebih dulu.
2. **Layar baru belum menerima input selama transisi masuknya** (250ms, atau 0 bila animasi mati). Ini menutup F3: ketukan kedua yang jatuh di PIN pad atau di tombol "Next" langkah berikutnya diabaikan.

Ketikan PIN yang cepat tetap utuh (Q-E4 AC5): penahan hanya berlaku 250ms pertama setelah PIN pad tampil, tidak antar-tombol keypad. Tombol keypad tidak pernah memakai penahan 600ms.

### 4.4 OrvynButton

| Aturan | Nilai |
|---|---|
| Tinggi | `heightIn(min = 52.dp)`, bukan tinggi tetap. Padding vertikal 14dp. Label boleh membungkus ke 2 baris atau lebih, rata tengah, tidak pernah terpotong |
| Lebar | Penuh di alur. Tombol "Try again" di error state lebar sesuai isi, minimal 120dp |
| Sibuk | Spinner 22dp menggantikan label, lebar dan tinggi tetap (tinggi diambil dari label), tidak bisa diketuk. TalkBack membaca label diikuti "Busy" (`stateDescription`) |
| Nonaktif | Isi `onSurface` 12%, label `onSurface` 38%. Label menjelaskan alasannya ("Enter an amount", "Minimum Rp10,000"). TalkBack membaca label + "disabled" |
| Sekali tembak | Lihat 4.3 |
| Jenis | Primary (isi `primary`), Secondary (tonal `primaryContainer`), Destructive (isi `error`, label `onError`), Destructive text (label `error`, ikon `ph_trash` 20dp, tanpa isi; hanya untuk aksi yang membuka dialog konfirmasi) |
| Di hero (layar sukses) | Primary putih dengan label Nerazzurro. Secondary bergaris putih 1dp pada 56% dengan label putih. Lihat 4.8 |

Semua tombol di layar Sign in, Welcome, dan bottom bar ikut aturan ini (Q-A6 AC3, AC4).

### 4.5 Nominal yang mengecil otomatis (AmountText)

Dipakai di: `AmountDisplay` (layar nominal), saldo hero beranda, nominal layar sukses, nominal valas, saldo di detail rekening.

1. Ukur teks pada ukuran gayanya (`displayBalance` 34sp kali `fontScale`).
2. Bila lebih lebar dari ruang yang ada, kecilkan ukurannya sampai muat, paling kecil **24sp** (24sp ikut `fontScale`, jadi di 2,0 berarti 48dp).
3. Bila di 24sp tetap tidak muat, teks membungkus **hanya setelah koma ribuan**: sisipkan U+200B (zero-width space) setelah setiap koma pada teks yang digambar. Contoh di 360dp 2,0: `Rp834,912,` / `398`. Tidak pernah `Rp834,912,39` / `8`.
4. TalkBack membaca nominal asli tanpa U+200B (`contentDescription` = `Rp834,912,398`).
5. Ukuran dihitung dari **nilai akhir**, bukan nilai yang sedang dihitung naik, supaya ukuran tidak berubah selama animasi 600ms (Q-A4 AC4). Animasi berakhir di angka yang tepat.
6. Di font 1,0, saldo hero tetap 34sp kecuali nominalnya sangat panjang (Q-A4 AC3).
7. Ikon mata di hero berada di luar lebar teks, jadi tidak ikut terdorong.

### 4.6 Layar PIN (PinScreen dan PinPad)

Dipakai di semua tempat PIN dimasukkan atau dibuat: Unlock, Create PIN (pendaftaran, masuk pertama, setelah Forgot PIN), Change PIN (tiga langkah), PIN transaksi (`PinConfirm` dan `EnterPin`: Transfer, terjadwal, QRIS, Bills, tagihan kartu, bayar Money Request, buka deposito, cair awal deposito, valas, investasi), dan lihat detail kartu.

**Tata letak**

```
┌───────────────────────────────────────────┐
│ Top bar 56dp: panah kembali (bila ada)      │  tetap
├───────────────────────────────────────────┤
│ AREA ATAS (weight 1, bisa digulir,          │
│ rata bawah bila muat)                        │
│   Judul                                      │
│   Kalimat penjelas                           │
│   24dp                                       │
│   ● ● ● ○ ○ ○   titik 16dp, jarak 16dp     │
│   16dp                                       │
│   Slot status (wrap, rata tengah)            │
│   [font besar: footer Unlock pindah ke sini] │
├───────────────────────────────────────────┤
│ AREA MENEMPEL                                │  tidak ikut digulir
│   Keypad 3×4                                 │
│   [font < 1,5: footer Unlock di sini]        │
│   16dp + inset navigasi                      │
└───────────────────────────────────────────┘
```

| Bagian | Font < 1,5 | Font ≥ 1,5 |
|---|---|---|
| Judul | `headlineMedium`, boleh 2 baris | `titleLarge`, satu baris. Bila tidak muat, mengecil otomatis seperti 4.5 sampai 18sp. Bila tetap tidak muat (nama sangat panjang), membungkus ke baris kedua, tidak pernah elipsis |
| Kalimat penjelas | `bodyLarge`, `textSecondary` | Tidak digambar. Teksnya digabung ke label aksesibilitas judul: `"{judul}. {kalimat}"`, jadi tetap dibaca TalkBack dan tetap ada di `uiautomator dump` (Q-A1 AC4) |
| Titik | 6 × 16dp | Sama (tidak ikut font) |
| Slot status | `heightIn(min = satu baris bodyMedium)`, membungkus ke baris berikutnya, tidak pernah terpotong | Sama |
| Tombol keypad | Tinggi 64dp, jarak 8dp | Tinggi 64dp. Di layar pendek: 56dp (tetap di atas 48dp) |
| Angka keypad | `headlineMedium` | Berhenti di 1,5× (4.1) |
| Footer Unlock | Di area menempel, di bawah keypad | Pindah ke akhir area atas, di bawah slot status |

Saat isi slot status berubah, area atas menggulir supaya slot itu terlihat penuh (`bringIntoView`). Jadi pesan "Wrong PIN. 2 tries left." selalu terbaca tanpa menggulir manual.

Anggaran tinggi di 360×640dp font 2,0 (Unlock, kasus terberat): area menempel = keypad 4×56 + 3×8 = 248dp + 16dp. Sisa sekitar 280dp untuk area atas, cukup untuk judul satu baris, titik, dan pesan dua baris. Footer dan label demo ada di bawahnya dan bisa digulir. Keempat baris keypad, termasuk `0` dan hapus, selalu tampil utuh (Q-A1 AC1, AC2).

**Keadaan PinPad**

| Keadaan | Titik | Keypad | Slot status | Haptik dan gerak | TalkBack |
|---|---|---|---|---|---|
| Kosong | 6 garis kosong (`surface` + garis `outlineStrong`) | Aktif | Kosong, atau info (`textSecondary`) | | Titik: `0 of 6 digits entered` |
| Mengetik | n terisi `primary` | Aktif | Kosong | | `{n} of 6 digits entered` |
| **Sibuk** (setelah digit ke-6, menunggu server) | 6 terisi, berdenyut alpha 1,0 → 0,4 → 1,0 selama 1,2 detik. Animasi mati: diam terisi | Alpha 38%, nonaktif (ketukan tidak menambah digit). Tombol biometrik dan panah kembali juga nonaktif. Back sistem diabaikan | Teks sibuk (tabel di bawah), `bodyMedium`, `textSecondary` | | Teks sibuk diumumkan sekali (live region polite pada slot; pengumuman tidak diulang selama teks sama). Titik: `6 of 6 digits entered` |
| PIN ditolak (salah, lemah, tidak cocok) | Goyang horizontal 300ms, lalu kosong | Aktif lagi | Pesan error: ikon `ph_warning_circle` 16dp + teks `error` | Getar REJECT | Pesan diumumkan (live region assertive) |
| Error yang bukan PIN (saldo kurang, limit, offline, server) | Tidak goyang | | | Tanpa getar | Layar PIN ditutup, kembali ke layar periksa. Pesan muncul di sana (4.12) |
| Berhasil | Tetap terisi sampai layar berikutnya tampil | Tetap nonaktif | Teks sibuk tetap | Getar CONFIRM hanya bila uang bergerak (dari layar sukses) | |
| Terkunci (salah ke-5) | | | | | Aplikasi pindah ke layar terkunci (6.9) |

**Teks sibuk per layar** (Q-B5 AC1). `{nominal}` memakai format uang biasa, valas memakai format valas.

| Layar | Teks |
|---|---|
| Unlock, lihat detail kartu, Change PIN langkah 1 | `Checking your PIN…` |
| Create PIN (pendaftaran tidak memanggil server di sini, jadi tidak ada keadaan sibuk); Create PIN masuk pertama dan setelah Forgot PIN | `Saving your PIN…` |
| Change PIN langkah 3 | `Saving your new PIN…` |
| Transfer kirim sekarang | `Sending {Rp500,000}…` |
| Transfer terjadwal | `Scheduling {Rp500,000}…` |
| QRIS, Bills, tagihan kartu, bayar Money Request | `Paying {Rp25,000}…` |
| Buka deposito | `Opening your deposit…` |
| Cair awal deposito | `Withdrawing {Rp1,000,000}…` |
| Beli valas / jual valas | `Buying {USD 100.00}…` / `Selling {USD 100.00}…` |
| Beli / jual investasi (Rupiah) | `Buying {Rp100,000}…` / `Selling {Rp50,000}…` |
| Beli emas dalam gram | `Buying {0.05 g}…` |

Teks lama `Sending…` dan `One moment…` di atas pad dihapus. Teks sibuk tidak menggeser tata letak karena slot sudah dipesan.

**Pesan PIN ditolak**

| Kasus | Teks |
|---|---|
| PIN salah, sisa 2 sampai 4 | `Wrong PIN. {2} tries left.` |
| PIN salah, sisa 1 | `Wrong PIN. 1 try left.` |
| PIN lemah (Create PIN, PIN baru di Change PIN) | `Choose 6 digits that aren't all the same or in a row.` |
| PIN kedua tidak cocok | `Those PINs didn't match. Start again.` |
| PIN lama salah di Change PIN | 7.1 |

**Layar aman (Q-C4).** Selama PinPad ada di komposisi, jendela memasang `FLAG_SECURE` (pola `DisposableEffect` yang sama dengan detail kartu), dan melepasnya saat PinPad hilang. Thumbnail di Recents juga hitam.

**Terang dan gelap.** Latar `background`. Tombol keypad `surface` dengan label `text`. Titik terisi `primary` (`#0032A0` / `#7FA2FF`). Error `error`. Teks sibuk `textSecondary`. Tidak ada warna baru.

**Aksesibilitas.** Setiap tombol keypad punya label (`1`..`9`, `0`, `Delete`, `Use fingerprint`). Urutan baca: judul (dengan kalimat penjelas), titik, slot status, keypad baris demi baris, footer. Target 64×(≥96)dp, minimal 56dp di layar pendek.

### 4.7 Layar nominal dengan keypad menempel

Dipakai di: nominal Transfer, QRIS (nominal diketik), tagihan kartu "Another amount", minta uang, split bill (total), pindah uang kantong, buka deposito, beli/jual Fund, Bond, dan Gold (Rupiah dan gram), beli/jual valas, top up e-wallet, dan nominal QR-ku (baru, 10.4).

```
┌───────────────────────────────────────────┐
│ Top bar 56dp: panah kembali                │  tetap
├───────────────────────────────────────────┤
│ AREA ATAS (weight 1, bisa digulir,          │
│ isi di tengah bila muat)                     │
│   Judul (headlineMedium)                     │
│   Kalimat atau baris konteks (penerima,      │
│   pilihan Buy/Sell, mata uang, Rupiah/Grams) │
│   Nominal (AmountText 4.5)                   │
│   Error inline ATAU "Available Rp…"          │
│   Quick Amount (FlowRow, rata tengah)        │
│   Info tambahan ("You pay Rp…")              │
├───────────────────────────────────────────┤
│ AREA MENEMPEL                                │
│   Keypad 3×4                                 │
│   12dp                                       │
│   Tombol utama (4.4)                         │
│   16dp + inset navigasi                      │
└───────────────────────────────────────────┘
```

- Keypad dan tombol utama tidak pernah ikut digulir (Q-A2 AC3). Tombol keypad 64dp, 56dp di layar pendek pada font ≥ 1,3. Angka berhenti di 1,5×.
- Saat error inline muncul, area atas menggulir supaya error terlihat.
- Quick Amount tetap seperti 0.8.1: FlowRow, chip minimal 48×48dp, membungkus rata tengah, label `k`/`M`, TalkBack membaca nominal penuh. Gaya chip mengikuti 4.9 (tidak dipilih bergaris, dipilih dengan centang).
- Label tombol utama boleh membungkus ke 2 baris di font 2,0 (Q-A2 AC5).
- **Tidak ada field teks di layar ini** (PO-19, Q-G9). Catatan diisi di langkah periksa berikutnya.
- Anggaran di 360×640dp font 2,0: area menempel ≈ 248 + 12 + 80 (tombol 2 baris) + 16 = 356dp. Area atas ≈ 160dp, cukup untuk nominal. Quick Amount bisa dicapai dengan menggulir.

**Keadaan**

| Keadaan | Tampilan |
|---|---|
| Kosong | Nominal `Rp0` berwarna `textTertiary`. Tombol nonaktif `Enter an amount` |
| Di bawah minimum | Tombol nonaktif `Minimum {Rp10,000}` (nilai per produk) |
| Error (saldo kurang, limit) | Teks `error` di bawah nominal, live region polite. Tombol nonaktif dengan label minimum atau label biasa |
| Data saldo/limit sedang dimuat | Baris "Available" diganti blok skeleton 160×20dp. Tombol nonaktif dengan label biasa |
| Data saldo/limit gagal dimuat | Di bawah nominal: `Couldn't load your balance and daily limit.` (teks `error` dengan ikon) dan tombol Secondary `Try again`. Tombol utama nonaktif `Balance didn't load` |
| Offline | Sama dengan gagal dimuat. Pesannya `You're offline. Check your connection and try again.` |
| Siap | Tombol aktif dengan label aksi ("Continue", "Pay Rp25,000", "Buy Rp100,000") |

### 4.8 Layar sukses

```
┌───────────────────────────────────────────┐
│ Hero gradient + garis jersey (tetap)        │
│ AREA ATAS (weight 1, bisa digulir,          │
│ di tengah vertikal bila muat)                │
│   Centang 88dp (font besar: 64dp)            │
│   Headline (titleMedium, heroTextMuted)      │
│   Nominal (AmountText 4.5, putih)            │
│   Subline (bodyLarge, putih)                 │
│   24dp                                       │
│   Kartu rincian putih, radius 14:            │
│     Date        16 Oct 2026, 19:27 WIB       │
│     ... baris lain ...                       │
├───────────────────────────────────────────┤
│ AREA MENEMPEL (di atas inset navigasi)       │
│   [Save {Sinta} as a recipient]  teks putih  │
│   [Share receipt]   bergaris putih           │
│   [Done]            isi putih                │
└───────────────────────────────────────────┘
```

- **Done selalu terlihat dan bisa diketuk** di atas navigasi sistem (Q-A3 AC1). Rincian yang panjang bisa digulir sampai baris terakhir (AC2).
- Area menempel transparan di atas gradien. Garis rambut putih 12% di atasnya hanya tampil saat ada isi yang tergulir di bawahnya.
- Urutan dari atas: aksi tersier (teks putih), aksi sekunder (bergaris), Done (utama). Jarak antar tombol 8dp, padding samping 20dp, bawah 16dp + inset.
- **Baris pertama rincian selalu `Date`** (Q-F2 AC3), format `16 Oct 2026, 19:27 WIB`, diambil dari waktu transaksi yang dikirim server (bukan jam HP) supaya sama dengan detail transaksi. Untuk transaksi terjadwal, `Date` adalah waktu jadwal dibuat.
- **`Share receipt`** (bergaris, 52dp, lebar penuh) hanya di sukses yang memindahkan uang ke pihak lain (PO-10): Transfer kirim sekarang, QRIS, Bill Payment, bayar Money Request. Membuat gambar bukti yang sama dengan bukti dari riwayat (S1.11 AC3) di cache aplikasi dan membuka menu bagikan Android. Tidak disimpan ke galeri. Saat membuat gambar: spinner di tombol. Gagal: snackbar `Couldn't make the receipt. Try again.`
- Tidak bisa di-Back ke PIN. Back sistem sama dengan Done.
- **TalkBack:** saat tampil, fokus ke headline, dan satu pengumuman `"{headline}, {nominal}, {subline}"`. Setiap baris rincian digabung label dan nilai (`"Date, 16 Oct 2026, 19:27 WIB"`). Tombol berurutan sesuai tampilan.
- **Terang dan gelap:** layar sukses sama di dua tema (hero selalu gelap, kartu selalu putih dengan teks `#0B1020` / `#4D5872`). Ikon status bar putih.

**Isi per layar sukses**

| Layar | Headline | Subline | Baris rincian | Aksi |
|---|---|---|---|---|
| Transfer sekarang | `Sent` | `to {Sinta Maharani}` | Date, To, Method, Note (bila ada), Reference | `Save {Sinta} as a recipient` (penerima baru), `Share receipt`, `Done` |
| Transfer terjadwal | `Scheduled` | `to {nama}` | Date, To, Method, Note, First send, Repeats | `Done` |
| QRIS | `Paid` | `to {merchant}` | Date, Merchant, Method, Reference | `Share receipt`, `Done` |
| Bill Payment | `Paid` | sama dengan 0.8.1 | Date, baris 0.8.1 (Your token, Name, Reference) | `Copy token` (PLN token, tetap), `Share receipt`, `Done` |
| Tagihan kartu | `Paid` | 0.8.1 | Date, Paid from, Still owed, Reference | `Done` |
| Bayar Money Request (baru lewat review) | `Paid` | `to {Sinta Maharani}` | Date, To, Note, Reference | `Share receipt`, `Done` |
| Minta uang terkirim | `Request sent` | `asked of {nama}` | Date, Note, Open for | `Done` |
| Split terkirim | `Requests sent` | 0.8.1 | Date, baris 0.8.1 | `Done` |
| Kantong | 0.8.1 | 0.8.1 | Date, baris 0.8.1 | `Done` |
| Buka deposito | `Deposit opened` | 0.8.1 | Date, Matures, Interest after tax, Reference | `Done` |
| Cair awal deposito (baru) | 6.2 | | | |
| Valas | `Bought` / `Sold` | `for {Rp1,642,000}` | Date, Rate, Reference | `Done` |
| Investasi | 0.8.1 | 0.8.1 | Date, baris 0.8.1 | `Done` |

### 4.9 Chip, filter chip, dan segmented button

**Chip pilihan** (Quick Amount, pilihan pendaftaran, mata uang, pengulangan jadwal, saran Vyna, rentang grafik):

| Keadaan | Latar | Garis | Label | Ikon |
|---|---|---|---|---|
| Tidak dipilih | `surface` | 1dp `outlineStrong` | `text`, `labelMedium` | |
| Dipilih | `primary` | tidak ada | `onPrimary` | `ph_check` 16dp di depan label, warna `onPrimary` |
| Ditekan | Lapisan state 12% di atas latar | | | |
| Nonaktif | Alpha 38% | | | |

- Bentuk pill (`full`), tinggi minimal 48dp, lebar minimal 48dp, padding samping 16dp (12dp bila ada centang). Jarak antar chip 8dp. FlowRow, tidak pernah gulir horizontal.
- Ikon centang membuat pilihan tidak hanya dibedakan warna.
- TalkBack: role `Checkbox` untuk multi-pilih, `RadioButton` untuk pilih-satu, dengan `selected`. Dibaca misalnya `"Every month on the 18th, selected"`. Quick Amount tetap membaca nominal penuh.
- Chip tidak diletakkan di atas `surfaceVariant` (3.3).

**Filter chip** (riwayat): gaya sama. Chip yang membuka sheet punya ikon `ph_caret_down` 16dp di belakang label. Saat filter aktif, chip tampil dipilih dan labelnya berisi pilihan (10.3).

**Segmented button** untuk tepat dua pilihan yang saling eksklusif: `Send now` / `Schedule` (review Transfer), `Buy` / `Sell` (valas), `Renew` / `Pay out` (instruksi deposito), `Rupiah` / `Grams` (beli emas).

| Bagian | Nilai |
|---|---|
| Komponen | Material 3 `SingleChoiceSegmentedButtonRow`, lebar penuh, tinggi minimal 48dp, radius pill |
| Tidak dipilih | `surface`, garis 1dp `outlineStrong`, label `text` |
| Dipilih | `primary`, label `onPrimary`, ikon `ph_check` 16dp |
| Label panjang di font besar | Membungkus ke 2 baris di dalam segmen, segmen bertambah tinggi |
| TalkBack | Role `RadioButton`, dibaca `"Schedule, selected, 2 of 2"` |

### 4.10 Pola memuat: skeleton, isi, error dengan "Try again"

Setiap layar yang memuat data dari server punya empat keadaan.

| Keadaan | Tampilan |
|---|---|
| Memuat, kurang dari 300ms | Latar kosong (tanpa kedipan skeleton) |
| Memuat, 300ms sampai 15 detik | Skeleton (4.10.1) |
| Isi | Konten |
| Kosong | `MessageState` kosong (ikon di lingkaran `primaryContainer`, judul, satu kalimat, satu aksi bila ada) |
| Error, atau memuat lebih dari 15 detik | `MessageState` error dengan tombol `Try again` |
| Offline dengan data cache | Konten cache + banner status (4.13) di atas konten |
| Offline tanpa cache | `MessageState` error, isi `You're offline. Check your connection and try again.` |

**MessageState error**

| Bagian | Nilai |
|---|---|
| Ikon | `ph_warning_circle` 36dp, `error`, di lingkaran 72dp `errorContainer` (beda dari kosong yang memakai `primaryContainer`) |
| Judul | Per layar (tabel 6.3), `titleLarge` |
| Isi | Pesan server bila ada. Offline: `You're offline. Check your connection and try again.` Tanpa pesan (misalnya waktu habis 15 detik): `Couldn't load this. Check your connection and try again.` |
| Aksi | `OrvynButton` Secondary `Try again`, lebar sesuai isi (minimal 120dp), 48dp. Wajib di setiap error state |
| Saat dicoba lagi | Tombol sibuk (spinner), pesan tetap tampil sampai hasil datang. Berhasil: isi menggantikan error tanpa pindah layar (Q-B3 AC2). Gagal lagi: pesan diperbarui, tombol aktif lagi |
| TalkBack | Judul dan isi digabung, diumumkan sekali (live region polite) saat error muncul |

Di dalam kartu kecil atau bagian layar (bukan seluruh layar), error ditulis sebagai baris inline: ikon `ph_warning_circle` 20dp + teks `bodyMedium` `error` + tombol teks `Try again` 48dp. Pesan bawaannya juga `Couldn't load this. Check your connection and try again.`

#### 4.10.1 Skeleton (Q-E12)

- Blok `surfaceVariant` dalam bentuk tata letak akhir, radius 8dp (kartu 14dp).
- Denyut alpha 0,55 → 1,0, satu siklus 1,2 detik, linear, berulang bolak-balik. Animasi mati: diam.
- Tampil hanya setelah 300ms menunggu.
- **Satu pengumuman per layar.** Blok-blok skeleton tidak punya semantics. Kontainer layar membawa `contentDescription = "Loading"` sekali, dan fokus TalkBack tidak berpindah ke tiap blok.
- Saldo hero saat memuat: blok skeleton 200×40dp berwarna putih 16% di atas gradien (bukan `Rp—`).

### 4.11 Snackbar

| Bagian | Nilai |
|---|---|
| Komponen | Material 3 Snackbar. Latar `inverseSurface` (`#0A0B10` terang, `#E8ECF6` gelap), teks `inverseOnSurface`, aksi `inversePrimary`. Radius 14dp, margin 12dp |
| Posisi | Bawah, tepat di atas area aksi yang menempel (tombol utama, keypad, atau bottom nav). Tidak pernah menutupi tombol utama |
| Error | Diawali ikon `ph_warning_circle` 20dp warna `inverseOnSurface` (bentuk ikon membedakan error dari info, tidak dengan warna) |
| Durasi | Info tanpa aksi: 4 detik. Dengan aksi (misalnya `Try again`): 10 detik. Bila TalkBack aktif dan ada aksi: tetap sampai ditutup, dengan tombol tutup `ph_x` berlabel `Dismiss` |
| Jumlah | Satu sekaligus. Snackbar baru menutup yang lama |
| TalkBack | Diumumkan polite, tidak mengambil fokus |
| Di dalam sheet | Tidak dipakai (snackbar tertutup scrim). Pakai kotak error di sheet (4.12) |

### 4.12 Kotak error form dan error di sheet

Untuk kegagalan yang bukan milik satu field: layar periksa setelah PIN gagal (bukan PIN), tagihan kartu, cair awal deposito, bayar permintaan, Bills "Choose what to pay", Change password (selain password lama salah), About you (ringkasan), sheet (simpan penerima, ganti kategori, instruksi deposito, anggaran).

| Bagian | Nilai |
|---|---|
| Letak | Tepat di atas tombol utama layar atau sheet |
| Gaya | Kotak `errorContainer`, radius 10dp, padding 12dp. Ikon `ph_warning_circle` 20dp `error`, teks `bodyMedium` `text` (bukan merah, supaya tetap terbaca panjang) |
| Muncul | Area yang bisa digulir menggulir supaya kotak terlihat. Live region polite, diumumkan sekali |
| Hilang | Saat nasabah mengubah sesuatu atau mencoba lagi |

Contoh teks: pesan server (`You're Rp120,000 short.`, `That's over your daily limit. You can still send Rp4,500,000 today.`) atau `You're offline. Check your connection and try again.`

### 4.13 Banner status (offline dan gagal diperbarui)

Dipakai di beranda (Q-B6) dan detail rekening (Q-F1).

| Bagian | Offline | Gagal diperbarui (bukan offline, ada cache) |
|---|---|---|
| Latar | `warningContainer`, radius 14dp, padding 12dp, margin 20dp samping | Sama |
| Ikon | `ph_wifi_slash` 20dp, `warning` | `ph_warning_circle` 20dp, `warning` |
| Baris 1 (`bodyMedium` 600, `text`) | Beranda: `You're offline. These balances are from {Yesterday, 21:40}.` Detail rekening: `You're offline. These details are from {Yesterday, 21:40}.` | `Couldn't refresh. Showing balances from {Yesterday, 21:40}.` |
| Baris 2 (`bodyMedium`, `textSecondary`) | Beranda: `Sending money works again once you're back online.` Detail rekening: tidak ada | Tidak ada |
| Aksi | Tidak ada (tarik untuk refresh) | Tombol teks `Try again` 48dp, rata kanan di bawah teks |
| Hari | `Today`, `Yesterday`, atau `Mon, 12 Oct` (fungsi `dayLabel`), lalu koma dan jam `21:40` | Sama |
| TalkBack | Satu node: seluruh teks banner, diumumkan polite saat muncul | Teks + tombol terpisah |
| Font besar | Teks membungkus, banner bertambah tinggi, tidak terpotong | Sama |

### 4.14 ListRow di font besar (Q-A6)

| Font | Susunan |
|---|---|
| < 1,5 | Sama dengan 0.8.1: tile ikon 40dp, judul + subjudul (weight 1), nominal di kanan. Nominal dibatasi paling lebar 45% baris, judul mendapat sisanya |
| ≥ 1,5 | Tile ikon rata atas. Kolom penuh: judul (`titleMedium`, membungkus per kata), subjudul, lalu nominal di baris sendiri, rata kiri, jarak 4dp |

- Judul tidak pernah `maxLines = 1`. Tinggi minimal 64dp, bertambah sesuai isi.
- TalkBack: satu node per baris: `"{judul}, {subjudul}, {nominal}"`, role Button bila bisa diketuk. Nominal dibaca dengan tanda: `"minus Rp50,000"` / `"plus Rp5,000,000"`.
- Berlaku untuk semua daftar yang memakai `ListRow` atau `TransactionRow` (beranda, riwayat, kantong, kartu, detail rekening, kategori Insights).

### 4.15 Baris sakelar (SwitchRow)

| Bagian | Nilai |
|---|---|
| Struktur | Seluruh baris `toggleable(value, role = Role.Switch)`. `Switch` di dalamnya `onCheckedChange = null` dan tanpa semantics sendiri. Satu ketukan di mana saja pada baris mengubah sakelar sekali (Q-E5 AC4) |
| TalkBack | `"{judul}, {subjudul}, switch, on"` / `"... off"`. `uiautomator dump`: `checkable=true`, `checked` sesuai posisi |
| Tinggi | Minimal 56dp, bertambah di font besar. Judul dan subjudul membungkus |
| Menunggu server (kartu, 7.6) | Sakelar langsung pindah. Spinner 16dp `primary` tampil di kiri sakelar. Baris nonaktif sampai server menjawab. TalkBack menambahkan `"Saving"` ke `stateDescription` |
| Berlaku untuk | Freeze card, Online payments, Overseas payments, Contactless, Sign in with fingerprint, Read replies aloud (Vyna), Auto-save kantong, dan sakelar lain di aplikasi |

### 4.16 Dialog konfirmasi dan tombol destruktif

| Bagian | Nilai |
|---|---|
| Dialog | Judul `titleLarge`, satu kalimat `bodyLarge` `textSecondary`, dua tombol rata kanan |
| Tombol konfirmasi destruktif | `OrvynButton` Destructive kompak: isi `error`, label `onError`, tinggi minimal 48dp, lebar sesuai isi |
| Tombol batal | Tombol teks `primary`. Labelnya menyebut yang tetap: `Cancel` secara umum, `Keep request` untuk permintaan uang (PO-15) |
| Sibuk | Tombol konfirmasi menampilkan spinner, dialog tetap terbuka sampai server menjawab. Berhasil: dialog tertutup. Gagal: dialog tertutup, pesan muncul (snackbar, atau kotak error bila dialog dibuka dari sheet) |
| Di sheet | Aksi destruktif memakai Destructive text (4.4), diletakkan di bawah tombol utama dengan jarak minimal 24dp, dan selalu membuka dialog |
| TalkBack | Dialog diumumkan dengan judulnya. Fokus awal ke judul, bukan ke tombol destruktif |

### 4.17 Status chip

Gaya Pill yang sudah ada: ikon 14dp + teks `labelMedium`, tidak pernah warna saja.

| Status | Teks | Ikon | Teks / latar |
|---|---|---|---|
| Transaksi berhasil | `Successful` | `ph_check_circle` | `success` / `successContainer` |
| Diproses | `Processing` | `ph_clock_counter_clockwise` | `warning` / `warningContainer` |
| Gagal | `Failed` | `ph_x_circle` | `error` / `errorContainer` |
| Dibatalkan petugas | `Reversed` | `ph_arrows_left_right` | `textSecondary` / `surfaceVariant` |
| Permintaan: menunggu orang lain | `Waiting` | `ph_clock` (baru) | `textSecondary` / `surfaceVariant` |
| Permintaan: kamu yang harus bayar | `To pay` | `ph_hand_coins` | `warning` / `warningContainer` |
| Permintaan: dibayar | `Paid` | `ph_check_circle` | `success` / `successContainer` |
| Permintaan: ditolak | `Declined` | `ph_x_circle` | `textSecondary` / `surfaceVariant` |
| Permintaan: dibatalkan | `Cancelled` | `ph_prohibit` (baru) | `textSecondary` / `surfaceVariant` |
| Permintaan: kedaluwarsa | `Expired` | `ph_clock_counter_clockwise` | `textSecondary` / `surfaceVariant` |

---

## 5. A. Font besar

### 5.1 Q-A1 Layar PIN tahan font besar

**Layar:** semua layar PIN, dibangun dengan 4.6. Unlock punya footer: `Forgot PIN?` (baru, 8.6), `Not you? Sign in with a password` (tetap), dan `Demo · ORVYN Bank is a fictional bank` (tetap, `labelMedium` `textTertiary`). Footer tersusun bertumpuk, rata tengah, setiap tombol teks 48dp.

| State | Tampilan |
|---|---|
| Default | 4.6 |
| Sibuk | `Checking your PIN…` (Unlock) atau teks per layar (4.6) |
| PIN salah | `Wrong PIN. {2} tries left.` membungkus ke 2 baris bila perlu, area atas menggulir ke slot itu |
| Terkunci | 6.9 |
| Offline | Unlock: slot status menampilkan `You're offline. Check your connection and try again.` tanpa goyang, titik kosong lagi. PIN transaksi: kembali ke layar periksa (4.12) |
| Loading, kosong | Tidak berlaku |

**Font 1,0 di 411dp:** sama dengan 0.8.1 kecuali footer Unlock yang bertambah satu baris (`Forgot PIN?`). Perbedaan ini disetujui CX (Q-A1 AC5).

**Aksesibilitas:** judul adalah heading. Pada font ≥ 1,5, heading membawa kalimat penjelas. Urutan baca di 4.6. Target sentuh di 4.6.

### 5.2 Q-A2 Layar nominal tahan font besar

**Layar:** semua layar di 4.7. Perubahan khusus per layar:

| Layar | Isi area atas | Tombol utama (aktif) | Tombol nonaktif |
|---|---|---|---|
| Transfer | Judul `How much?`, penerima, nominal, Quick Amount | `Continue` | `Enter an amount`, `Minimum Rp10,000`, `Balance didn't load` |
| QRIS diketik | `Pay {merchant}` / `How much?` (0.8.1) | `Pay {Rp25,000}` | `Enter an amount`, `Minimum Rp1,000` |
| Tagihan kartu "Another amount" | 0.8.1 | `Pay {Rp100,000}` | `Minimum Rp10,000` |
| Minta uang | `Ask {Sinta}` + kalimat 0.8.1, **tanpa field catatan** (11.9) | `Continue` | `Ask for at least Rp1,000` |
| Split total | 0.8.1 | `Next` | 0.8.1 |
| Kantong pindah uang | 0.8.1 | 0.8.1 | 0.8.1 |
| Buka deposito | 0.8.1 | `Next` | 0.8.1 |
| Fund, Bond, Gold | 0.8.1, plus segmented `Rupiah`/`Grams` untuk Gold beli (10.6) | `Buy {Rp100,000}` / `Sell {Rp50,000}` | 0.8.1 |
| Valas | Segmented `Buy`/`Sell`, chip mata uang, nominal valas (AmountText), baris "You pay"/"You get" | 11.5 | 11.5 |
| E-wallet | Kartu detail biller, nominal, Quick Amount, baris total | `Pay {Rp51,000}` | `Enter an amount`, `Minimum Rp10,000` (11.6) |
| QR-ku | 10.4 | 10.4 | 10.4 |

Tidak ada copy baru selain yang disebut. **Aksesibilitas:** urutan baca: judul, konteks, nominal, error atau Available, Quick Amount, keypad, tombol. Tombol `000` dibaca `Three zeros`.

### 5.3 Q-A3 Layar sukses tahan font besar

**Layar:** semua layar sukses, dibangun dengan 4.8. Di 360×640dp font 2,0, sukses transfer terjadwal dengan 6 baris (Date, To, Method, Note, First send, Repeats): rincian digulir, `Done` menempel di atas navigasi. Tombol sekunder (`Save {Sinta} as a recipient`, `Share receipt`) tetap di area menempel dan boleh membungkus 2 baris.

State: default dan berbagi bukti di 4.8. Loading, kosong, offline: tidak berlaku (layar ini tampil setelah server berhasil). Menyimpan penerima offline: snackbar offline, tombol tetap (6.1).

### 5.4 Q-A4 Nominal dan saldo tidak pernah patah di tengah angka

**Komponen:** AmountText (4.5) di saldo hero, kartu rekening beranda (`titleLarge`, aturan bungkus yang sama tanpa mengecil), `AmountDisplay`, nominal sukses, saldo detail rekening, nominal valas.

Contoh di 360dp font 2,0, saldo `Rp834,912,398`: mengecil sampai 24sp (48dp). Bila tetap tidak muat: `Rp834,912,` / `398`. Saldo tersembunyi `Rp••••••••` tidak berubah dan dibaca `Balance hidden` (9.7).

### 5.5 Q-A5 Beranda dan navigasi tahan font besar

| Bagian | Font < 1,5 | Font ≥ 1,5 |
|---|---|---|
| Baris atas hero | Sapaan (weight 1), badge tier, Vyna, Inbox dalam satu baris (0.8.1) | Baris 1: badge tier di kiri, Vyna dan Inbox di kanan. Baris 2: sapaan selebar penuh, membungkus per kata. Tidak pernah memotong kata ("Good mornin/g") |
| Aksi cepat | Satu baris 4 atau 5 item (0.8.1) | Grid. Jumlah kolom = berapa sel 112dp yang muat (360dp: 2 kolom, 411dp: 3 kolom). 5 aksi di 360dp: 2+2+1, di 411dp: 3+2. Label `labelMedium` boleh 2 baris, rata tengah, tidak memotong kata. Sel minimal 72dp tinggi |
| Bottom nav | Tinggi 64dp | `heightIn(min = 64.dp)`. Label berhenti di 1,5× (4.1). Label QRIS diletakkan di bawah tombol QRIS yang menonjol dalam satu kolom (tombol, 4dp, label), bukan dengan offset tetap, jadi tidak bertabrakan |
| Banner promo | Tinggi 156dp, teks 66% lebar | `heightIn(min = 156.dp)`. Teks selebar penuh di atas lapisan gelap penuh (Nero 86% di seluruh gambar), judul membungkus tanpa batas baris, pil aksi di bawah teks |
| Kartu kantong | Lebar 144dp, tinggi 144dp | Lebar 216dp, tinggi minimal 144dp, nama membungkus tanpa batas baris (nama 24 huruf bisa 2 sampai 3 baris) |
| Kartu rekening | 0.8.1 + bisa diketuk (10.1) | Nomor dan ikon salin membungkus. Saldo memakai aturan 4.5 |

Font 1,0: tata letak sama dengan 0.8.1 kecuali urutan bagian (11.1) dan banner status (6.6).

**Aksesibilitas:** aksi cepat tetap role Button dengan label katanya. Bottom nav role Tab dengan `selected`. Sapaan dibaca utuh.

### 5.6 Q-A6 Baris daftar dan tombol tahan font besar

- ListRow: 4.14. Contoh di 360dp font 2,0: `Sinta Maharani` membungkus `Sinta` / `Maharani`, subjudul di bawahnya, nominal `−Rp50,000` di baris sendiri.
- OrvynButton: 4.4. `Sign in with a passkey` menjadi 2 baris di 360dp font 2,0, tombol bertambah tinggi.
- Daftar layar yang diuji ada di `stories.md` definisi selesai poin 3.

---

## 6. B. Tidak ada kegagalan yang diam

### 6.1 Q-B1 Aksi yang gagal selalu memberi tahu

Aturan: tampilan tidak pernah berubah seolah berhasil sebelum server menjawab berhasil. Saat gagal, tampilan kembali ke keadaan sebelum aksi, dan pesan muncul di tempat ini:

| Aksi | Tempat pesan | Sibuk |
|---|---|---|
| Scheduled: Resume, Pause | Snackbar | Tombol baris menampilkan spinner |
| Scheduled: Delete (setelah dialog) | Snackbar | Tombol dialog (4.16) |
| Ganti kategori transaksi (sheet) | Kotak error di sheet, sheet tetap terbuka, pilihan kembali ke kategori lama | Pilihan yang diketuk menampilkan spinner 16dp |
| Simpan penerima (sheet nickname) | Kotak error di sheet | Tombol `Save` sibuk |
| Simpan penerima (layar sukses Transfer) | Snackbar di atas area menempel | Tombol teks sibuk |
| Hapus penerima tersimpan (dialog dari sheet) | Kotak error di sheet | 4.16 |
| Instruksi jatuh tempo deposito (segmented di sheet) | Kotak error di sheet, segmented kembali ke pilihan lama | Spinner 16dp di segmen yang diketuk |
| Tutup FX Account (dialog) | Snackbar | 4.16 |
| Hapus passkey (dialog) | Snackbar | 4.16 |
| Bayar tagihan kartu setelah PIN | Kotak error di atas tombol `Pay {Rp100,000}` di layar periksa tagihan, **bukan** kembali ke pilih nominal (AC2) | PIN sibuk |
| Cair awal deposito setelah PIN | Kotak error di layar periksa pencairan (6.2), deposito tetap di daftar (AC3) | PIN sibuk |
| Semua alur PIN lain | Kotak error di layar periksanya | PIN sibuk |

Pesan: offline `You're offline. Check your connection and try again.` Selain offline: pesan server. Bila ada field terkait (misalnya nama kantong, nominal), pesan server tampil di bawah field itu (AC4).

### 6.2 Q-B2 Layar sukses pencairan deposito lebih awal

**Alur baru:** Deposits → ketuk deposito (sheet) → `Withdraw early` (Destructive text) → **layar periksa pencairan** (baru) → PIN → sukses → `Done` → daftar Deposits. Back dari periksa kembali ke daftar (sheet tertutup). Back dari PIN kembali ke periksa. Sukses tidak bisa di-Back ke PIN.

**Layar periksa pencairan** (`FlowScreen`, rincian di kartu `surface`, tombol utama menempel di bawah):

| Bagian | Teks |
|---|---|
| Judul | `Withdraw early?` |
| Kalimat | `You get the principal back. The interest is lost.` |
| Baris | `Deposit` → `{3}-month deposit` |
| | `Matures` → `{3 Jan 2027}` |
| | `Back to ORVYN Everyday` → `{Rp1,000,000}` (`titleMedium`) |
| | `Interest you lose` → `{Rp15,343}` |
| Tombol utama | `Withdraw {Rp1,000,000}` (Primary: konsekuensinya sudah dijelaskan di layar ini) |
| PIN (kalimat, 0.8.1) | `To withdraw {Rp1,000,000} early. The interest is lost.` |
| Teks sibuk | `Withdrawing {Rp1,000,000}…` |

**Layar sukses** (4.8):

| Bagian | Teks |
|---|---|
| Headline | `Withdrawn` |
| Nominal | `{Rp1,000,000}` (nominal Journal Entry, sama dengan kenaikan saldo tabungan) |
| Subline | `back in ORVYN Everyday` |
| Baris | `Date` → `{16 Oct 2026, 19:27 WIB}` |
| | `From` → `{3}-month deposit` |
| | `Interest` → `Not paid, because you withdrew before {3 Jan 2027}` |
| | `Reference` → `{kode}` |
| Tombol | `Done` → daftar Deposits (deposito sudah tidak ada) |

State: offline atau gagal setelah PIN: kembali ke periksa dengan kotak error (6.1). Getar CONFIRM saat sukses tampil. Terang dan gelap: periksa mengikuti tema, sukses sama di dua tema.

### 6.3 Q-B3 Layar yang gagal memuat punya "Try again"

Semua layar di bawah memakai 4.10. Judul error:

| Layar | Judul error |
|---|---|
| Cards | `Couldn't load your cards` |
| Inbox | `Couldn't load your inbox` |
| History | `Couldn't load your history` |
| Requests | `Couldn't load your requests` |
| Detail permintaan | `Couldn't load this request` |
| Wealth | `Couldn't load your wealth` |
| Foreign currency | `Couldn't load rates` |
| Time deposits | `Couldn't load your deposits` |
| Investments | `Couldn't load investments` |
| Detail produk investasi | `Couldn't load this investment` |
| Risk quiz | `Couldn't load the quiz` |
| E-statements | `Couldn't load your statements` |
| Help | `Couldn't load help` |
| Insights | `Couldn't load your insights` |
| Kategori Insights | `Couldn't load these` |
| Pocket | `Couldn't load this pocket` |
| Scheduled | `Couldn't load your schedules` |
| Pilih penerima Transfer | `Couldn't load your recipients` |
| Bills | `Couldn't load this list` |
| QR-ku | `Couldn't make your QR` |
| Passkeys (baru) | `Couldn't load your passkeys` |
| Detail transaksi | `Couldn't load this transaction` |
| Detail rekening (baru) | `Couldn't load your account` |
| Nominal Transfer, nominal QRIS | Inline di 4.7 |
| Review Transfer (opsi BI-FAST/RTGS) | Inline di 6.4 |

- Passkeys yang gagal dimuat menampilkan error, tidak pernah `No passkeys yet.` (AC4).
- Tidak ada skeleton lebih dari 15 detik (AC3).
- Setiap judul di atas sudah ada di 0.8.1 kecuali tiga yang ditandai baru.

### 6.4 Q-B4 Layar periksa Transfer pulih setelah offline

**Layar:** `Check the details` (Review Transfer).

| State | Kartu rincian | Tombol kirim |
|---|---|---|
| Memuat opsi | Skeleton 180dp (4.10.1) | Nonaktif, `Loading the details…` |
| Gagal memuat (offline atau server) | Baris inline: `Couldn't load the fee and arrival time.` + tombol teks `Try again` 48dp. Di bawahnya pesan offline/server bila ada | Nonaktif, `Details didn't load` |
| Siap | Rincian 0.8.1 | `Send {Rp500,000}` / `Schedule {Rp500,000}` |
| Kembali dari PIN dengan error bukan PIN | Rincian **dimuat ulang otomatis**, kotak error (4.12) di atas tombol | Aktif setelah rincian siap |
| Offline saat kirim | Kotak error `You're offline. Check your connection and try again.` | Aktif. Mengetuknya memuat ulang rincian dulu (tombol sibuk), lalu membuka PIN |

Kunci idempotensi yang sama dipakai sampai transfer berhasil, jadi transfer terkirim sekali (AC4). Tidak ada keadaan "Loading" yang tidak pernah selesai.

### 6.5 Q-B5 PIN pad memberi tahu saat diproses

Seluruhnya di 4.6 (keadaan sibuk, teks per layar, pembedaan PIN salah dan error lain, animasi mati). Perubahan server terkait: pembayaran kartu dan semua jalur PIN mengembalikan `Wrong PIN. {N} tries left.` (bagian 13).

### 6.6 Q-B6 Beranda offline yang jujur

**Layar:** beranda.
- Banner status offline (4.13) **tepat di bawah kartu aksi cepat** (kartu aksi cepat menumpuk di tepi bawah hero, jadi ini posisi pertama di bawah hero), sebelum kartu insight.
- Aksi cepat: alpha 38%, **tetap bisa diketuk**. Ketukan menampilkan snackbar `You're offline. Try again when you're connected.` TalkBack label `"{Transfer}, unavailable while offline"`, role Button, tidak diumumkan "disabled".
- Gagal diperbarui karena hal lain dengan cache: varian "Couldn't refresh" dengan `Try again` (4.13).
- Setelah online dan tarik untuk refresh berhasil: banner hilang (fade 150ms) dan aksi cepat kembali 100%.
- Tidak ada cache dan offline: hero memakai skeleton saldo (4.10.1), dan di bawah aksi cepat tampil MessageState error `Couldn't load your accounts` dengan `Try again`.

Terang: `warningContainer` `#FFF1D6`, ikon `#9A5B00`. Gelap: `#33240A`, ikon `#F0B44C`. Kontras di 3.3.

### 6.7 Q-B7 Tarik untuk refresh memperbarui seluruh beranda

Tarik untuk refresh, kembali dari latar belakang, dan perubahan `DataVersion` memuat ulang **semua** bagian beranda: saldo dan rekening, Total assets di kartu Wealth, banner, kartu insight, jumlah inbox, kantong, transaksi terakhir.

- Indikator tarik `primary` di atas `surface`. Indikator hilang setelah semua bagian selesai atau gagal.
- Bagian yang gagal diperbarui tetapi punya cache tetap menampilkan cache. Bagian tanpa data dan tanpa cache **tidak ditampilkan beserta eyebrow-nya** (PO-24). Contoh: eyebrow `WEALTH` tidak tampil sendirian.
- Total assets dan Total balance diperbarui bersamaan. Selama Total assets dimuat ulang, angkanya tetap angka lama (tidak berkedip ke skeleton).

### 6.8 Q-B8 Maintenance langsung mengganti layar

**Layar maintenance** (menggantikan seluruh aplikasi, tanpa panah kembali):

| Bagian | Isi |
|---|---|
| Ikon | `ph_clock_counter_clockwise` 36dp di lingkaran 72dp `primaryContainer` |
| Judul | `ORVYN is being updated` (heading, satu-satunya judul) |
| Kalimat | Pesan dari server. Bila kosong: `Your money is safe. Try again in a few minutes.` |
| Tombol utama (menempel di bawah) | `Try again` |
| Masih maintenance setelah Try again | Teks di atas tombol, `textSecondary`, live region polite: `Still updating. Try again in a few minutes.` |
| Offline saat Try again | Teks yang sama tempatnya: `You're offline. Check your connection and try again.` |
| Maintenance selesai | Layar Unlock, atau beranda bila sesi masih berlaku |

Judul ganda `Back soon` dihapus. Pemicu (PO-8): jawaban server yang menyebut maintenance (layar langsung berganti, tanpa pesan inline), aplikasi kembali ke depan, tarik untuk refresh, dan cek `/config` setiap 60 detik selama aplikasi di depan. Transisi ke layar ini: fade 250ms. TalkBack: judul diumumkan saat layar muncul. Font besar: isi di area yang bisa digulir, tombol menempel.

### 6.9 Q-B9 Layar terkunci bisa dicoba lagi

**Layar:** Unlock dalam keadaan terkunci (juga tujuan dari PIN salah ke-5 di mana pun, PO-5).

| Bagian | Teks |
|---|---|
| Judul layar | `Welcome back, {Raka}` (tetap) |
| Ikon | `ph_lock_simple` 36dp di lingkaran 72dp `errorContainer`, ikon `error` |
| Judul pesan | `Your account is locked` |
| Kalimat (sama untuk dua sebab, PO-7) | `Ask the ORVYN team to unlock it. Once they have, tap Try again.` |
| Tombol utama (menempel) | `Try again` |
| Masih terkunci | Teks di atas tombol, `error` dengan ikon, live region: `It's still locked. Ask the ORVYN team to unlock it.` |
| Offline | `You're offline. Check your connection and try again.` di tempat yang sama |
| Sudah dibuka | PIN pad tampil. Slot status (info, `textSecondary`): `Your account is unlocked. Enter your PIN.` 5 percobaan |
| Footer | `Not you? Sign in with a password` dan label demo. `Forgot PIN?` disembunyikan selama terkunci |

Membuka aplikasi dari latar belakang setelah dibuka petugas langsung menampilkan PIN pad (AC5), dengan info yang sama di slot status. Sibuk: tombol `Try again` spinner. Kalimat server lama `Your account is locked after too many wrong tries...` diganti (bagian 13).

### 6.10 Q-B10 E-statement: satu PDF gagal tidak menghapus daftar

- Ketuk bulan: baris bulan menampilkan spinner 20dp menggantikan ikon bagikan, label TalkBack `Making your {September 2026} statement`. Teks lama `Making your PDF…` tetap sebagai teks di baris itu.
- Gagal: daftar tetap. Snackbar (4.11) `Couldn't make your {September 2026} statement.` dengan aksi `Try again` (10 detik). `Try again` membuat ulang PDF bulan itu lalu membuka menu bagikan.
- Gagal memuat daftar bulan: 6.3.

---

## 7. C. Keamanan dan admin

### 7.1 Q-C1 Ganti PIN memeriksa PIN lama dulu

**Alur:** Profile → `Change PIN` → (1) PIN lama → (2) PIN baru → (3) ulangi PIN baru → sukses → `Done` → Profile. Semua langkah memakai 4.6 dengan panah kembali. Back di langkah 2 atau 3 kembali ke langkah 1 dan mengosongkan PIN yang sudah diketik. Langkah berganti dengan transisi langkah (4.3).

| Langkah | Judul | Kalimat | Sibuk | Error |
|---|---|---|---|---|
| 1 | `Enter your current PIN` | `We check it before you choose a new one.` | `Checking your PIN…` | PIN lama salah, sisa 2 sampai 4: `That's not your current PIN. {4} tries left.` Sisa 1: `That's not your current PIN. 1 try left.` Salah ke-5: layar terkunci (6.9) |
| 2 | `Choose a new PIN` | `Avoid 123456 or your birthday.` | (tidak memanggil server) | PIN lemah: `Choose 6 digits that aren't all the same or in a row.` |
| 3 | `Enter the new PIN again` | `Just to be sure you remember it.` | `Saving your new PIN…` | Tidak cocok: kembali ke langkah 2 dengan `Those PINs didn't match. Start again.` |

PIN lama salah, lemah, dan tidak cocok memakai goyang dan REJECT (PIN ditolak). Offline di langkah 1 atau 3: slot status `You're offline. Check your connection and try again.` tanpa goyang, titik kosong, tidak menghitung percobaan.

**Sukses** (layar `FlowScreen` tanpa panah kembali, bukan hero karena tidak ada uang bergerak):

| Bagian | Isi |
|---|---|
| Ikon | `ph_check_circle` 36dp `success` di lingkaran 72dp `successContainer` |
| Judul | `Your PIN is changed` |
| Kalimat | `Use your new PIN from now on.` |
| Tombol utama (menempel) | `Done` |

TalkBack mengumumkan judul dan kalimat sekali. Terang dan gelap: token `success`/`successContainer` (5.39 dan 8.61 di `surface`).

### 7.2 Q-C2 Ganti password menghitung percobaan

**Layar:** `Change password` (tetap satu layar). Field: `Current password`, `New password` (helper `At least 8 characters, with letters and numbers.`). Tombol utama menempel: `Save password`. Field password lama punya tombol aksi keyboard "Next", password baru "Done" yang menjalankan simpan.

| Kasus | Tempat | Teks |
|---|---|---|
| Password lama salah, sisa 2 sampai 4 | Di bawah field **Current password** | `That's not your current password. {4} tries left.` |
| Sisa 1 | Sama | `That's not your current password. 1 try left.` |
| Salah ke-5 | Layar terkunci | 6.9 |
| Password baru lemah | Di bawah field **New password** | `Use at least 8 characters, with both letters and numbers.` |
| Offline atau error server lain | Kotak error di atas tombol (4.12) | `You're offline. Check your connection and try again.` / pesan server |

Field yang salah mendapat fokus dan layar menggulir ke pesannya. TalkBack membaca error field (`semantics { error }`).

**Sukses:** layar sama dengan 7.1. Judul `Your password is changed`, kalimat `Use your new password the next time you sign in on a phone.`, tombol `Done`.

### 7.3 Q-C3 Reset demo berhasil walau ada passkey

Admin: 12.3. Aplikasi: masuk dengan passkey yang sudah tidak ada di server menampilkan pesan di bawah tombol passkey di layar Sign in (tempat error Sign in yang sama, 8.1), tanpa crash:

`This passkey doesn't work anymore. Sign in with your phone number and password.`

### 7.4 Q-C4 PIN pad tidak bisa direkam layar

`FLAG_SECURE` selama PinPad tampil, dilepas saat hilang (4.6). Detail kartu tetap aman seperti 0.8.1. Tidak ada perubahan tampilan dan tidak ada izin baru.

### 7.5 Q-C5 Aksi destruktif selalu dikonfirmasi

Semua memakai 4.16.

| Pemicu | Judul | Kalimat | Konfirmasi | Batal |
|---|---|---|---|---|
| `Remove from saved` (sheet penerima) | `Remove {Sri Wahyuni} from saved?` | `You can save them again after your next transfer.` | `Remove` | `Cancel` |
| `Remove budget` (sheet anggaran) | `Remove your {Food and drinks} budget?` | `Reminders for this category stop.` | `Remove` | `Cancel` |
| `Close` (FX Account kosong) | `Close your {USD} account?` | `It's empty, so nothing moves. You can open it again when you buy {USD}.` | `Close account` | `Cancel` |

Di sheet penerima dan sheet anggaran: tombol utama (`Save`, `Save budget`) di atas, lalu jarak 24dp, lalu Destructive text (`Remove from saved`, `Remove budget`) dengan ikon `ph_trash`. Terang: tombol konfirmasi `#C62828` + putih. Gelap: `#FF8A80` + `#3A1416`.

### 7.6 Q-C6 Status Freeze dan sakelar kartu yang pasti

Memakai SwitchRow dengan keadaan menunggu (4.15).

| Sakelar | Snackbar setelah server berhasil (nyala / mati) |
|---|---|
| Freeze card | `Card frozen` / `Card unfrozen` |
| Online payments | `Online payments are off` / `Online payments are on` (mati / nyala sesuai posisi baru) |
| Overseas payments | `Overseas payments are off` / `Overseas payments are on` |
| Contactless | `Contactless is off` / `Contactless is on` |

- Snackbar dan visual kartu abu-abu (`Frozen`) baru muncul setelah server berhasil.
- Gagal: sakelar kembali ke posisi semula (animasi 150ms) dan snackbar error berisi pesan offline atau pesan server.

### 7.7 Q-C7 Server menolak penolakan pendaftaran tanpa alasan

Admin: 12.1. Tidak ada perubahan aplikasi. Pendaftar tetap menerima alasan lewat push dan inbox (0.8.1).

### 7.8 Q-C8 Password awal petugas tidak lewat URL

Admin: 12.2.

---

## 8. D. Daftar dan masuk

### 8.1 Q-D1 Sign in

**Layar:** `Sign in`. Seluruh isi (judul, kalimat, field, tombol) berada dalam satu kolom yang bisa digulir dengan `imePadding`. Tombol tidak menempel, supaya field dan pesan tidak terdesak oleh keyboard.

| Bagian | Isi |
|---|---|
| Judul | `Sign in` |
| Kalimat (Q-G5) | `Use your phone number and ORVYN password.` |
| Field 1 | `Phone number`, helper `For example 0812 3456 7890`, keyboard telepon, autofill nomor telepon, aksi keyboard **Next** (fokus ke password) |
| Field 2 | `Password`, tombol tampil/sembunyi, autofill password, aksi keyboard **Done** (menjalankan Sign in) |
| Tombol utama | `Sign in` |
| Tombol sekunder | `Sign in with a passkey` (bila fitur menyala), 16dp di bawah |

**Perilaku:**
- Saat field mendapat fokus, kolom menggulir supaya field itu, pesannya, dan tombol `Sign in` terlihat di atas keyboard (`bringIntoViewRequester`).
- Error tampil di bawah field Password, lalu kolom menggulir ke pesan itu tanpa menutup keyboard (AC3).

| State | Teks |
|---|---|
| Password salah | `That phone number and password don't match.` (dari server, 0.8.1) |
| Terkunci | Layar terkunci (6.9) |
| Offline | `You're offline. Check your connection and try again.` (AC5) |
| Passkey sudah tidak ada | 7.3 |
| Sibuk | Tombol `Sign in` spinner, field tetap bisa dibaca |
| Mode Forgot PIN | 8.6 |

Uji 200% di 360dp tanpa keyboard: field password dan `Sign in` bisa dicapai dengan menggulir (AC4).

### 8.2 Q-D2 About you

**Layar:** pendaftaran langkah `About you`.

**Saat `Continue` diketuk dan ada kesalahan:**
1. Layar menggulir ke field atau kelompok pertama yang salah, field itu mendapat fokus, dan TalkBack membacakan errornya.
2. Di atas `Continue` tampil kotak ringkasan (4.12 dengan ikon `ph_warning_circle`):
   - Satu: `1 answer needs a fix. It's marked above.`
   - Dua atau lebih: `{2} answers need a fix. They're marked above.`
3. Kelompok pilihan yang belum dijawab (`Work`, `Monthly income`, `Main reason for the account`) menampilkan `Pick one.` di bawah judul kelompok, dengan ikon, warna `error`. Teks lama `Pick one answer for each question.` dihapus.

**Cek NIK (PO-2):** setelah semua field lolos cek di HP, `Continue` menjadi sibuk (spinner, TalkBack `Checking your NIK`) dan memanggil server.

| Hasil | Tampilan |
|---|---|
| Tersedia | Lanjut ke langkah berikutnya |
| Sudah terdaftar (nasabah atau pendaftaran pending) | Di bawah field NIK: `This NIK is already registered with ORVYN.` Layar menggulir ke field, ringkasan `1 answer needs a fix. It's marked above.` |
| Cek ke-11 ditolak | Di bawah field NIK: `Too many NIK checks for this sign-up. Start again from your phone number.` Di bawahnya tombol teks `Start again` (kembali ke langkah nomor HP, data dihapus) |
| Offline | Kotak error di atas `Continue`: `You're offline. Check your connection and try again.` Tidak lanjut |
| Error server lain | Pesan server di kotak yang sama |

### 8.3 Q-D3 Create PIN

**Layar:** Create PIN di pendaftaran, masuk pertama, Change PIN langkah 2 (7.1), dan setelah Forgot PIN (8.6). Semua memakai 4.6.

| Langkah | Judul | Kalimat |
|---|---|---|
| 1 (pendaftaran) | `Create your PIN` | `You'll use these 6 digits to open ORVYN and approve payments. Avoid 123456 or your birthday. {Step 7 of 8}.` |
| 1 (masuk pertama) | `Create your PIN` | `You'll use these 6 digits to open ORVYN and approve payments. Avoid 123456 or your birthday.` |
| 2 | `Enter it again` | `Just to be sure you remember it.` |

| Kasus | Waktu | Hasil |
|---|---|---|
| PIN lemah (`123456`, `111111`, `654321`, aturan sama dengan server) | Langsung setelah digit ke-6 langkah 1, tanpa server | Goyang + REJECT, titik kosong, tetap di langkah 1, slot status: `Choose 6 digits that aren't all the same or in a row.` |
| Tidak cocok | Setelah digit ke-6 langkah 2 | Kembali ke langkah 1 (transisi mundur), goyang + REJECT, slot status: `Those PINs didn't match. Start again.` Pesan ini milik langkah 1, jadi terlihat (E2E-06) |
| Cocok | | Pendaftaran: kirim. Masuk pertama: `Saving your PIN…` lalu lanjut |

Pesan terbaca utuh di 360dp font 2,0 karena slot status membungkus (4.6).

### 8.4 Q-D4 Kode OTP tetap terlihat bila notifikasi mati

**Layar:** `Enter the code`.
- Notifikasi mati: tidak ada pindah otomatis. Setelah `Send code`, layar `Enter the code` tampil dengan kotak info di atas field kode.
- Kotak info: latar `primaryContainer`, radius 14dp, padding 16dp, ikon `ph_bell` 20dp `onPrimaryContainer`, teks `bodyLarge` `onPrimaryContainer`: `Notifications are off, so here's your code: {123456}`. Kode ditulis `titleMedium` tabular dan bisa dipilih untuk disalin.
- TalkBack membaca kode per angka: `Notifications are off, so here's your code: 1 2 3 4 5 6`.
- Kalimat layar (kedua keadaan): `Enter the 6-digit code for {0812 3456 7890}. {Step 2 of 8}.` Kata "as a notification" dihapus.
- Notifikasi menyala: sama dengan 0.8.1, kotak info tidak tampil.
- Teks lama `Notifications are off, so here's your demo code: {kode}` di layar nomor HP dihapus (kode hanya di layar Enter the code).

Terang: `#001A5C` di `#E5ECFA` (13.60). Gelap: `#DCE5FF` di `#0032A0` (8.50).

### 8.5 Q-D5 Langkah selfie punya panah kembali

Langkah `Take a selfie` menampilkan panah kembali di top bar 56dp, posisi sama dengan langkah KTP, label TalkBack `Back`, target 48dp. Panah dan Back sistem membawa ke `Photo of your KTP`. Data langkah sebelumnya tetap. Di atas pratinjau kamera, panah memakai ikon putih di lingkaran Nero 40% supaya terlihat di gambar apa pun.

### 8.6 Q-D6 "Forgot PIN?" di Unlock

**Alur:** Unlock → `Forgot PIN?` → sheet → `Sign in with password` → Sign in (mode Forgot PIN) → Create PIN (mode baru) → beranda. Back dari Sign in kembali ke Unlock. Back dari Create PIN kembali ke Unlock (PIN lama tetap berlaku sampai PIN baru tersimpan).

| Bagian | Teks |
|---|---|
| Tombol di Unlock | `Forgot PIN?` (tombol teks, di atas `Not you? Sign in with a password`) |
| Sheet, judul | `Forgot your PIN?` |
| Sheet, kalimat | `Sign in with your phone number and password, then choose a new PIN.` |
| Sheet, tombol utama | `Sign in with password` |
| Sheet, tombol teks | `Not now` |
| Sign in, kalimat (mode ini) | `Sign in with your password, then choose a new PIN.` |
| Create PIN, judul | `Choose a new PIN` |
| Create PIN, kalimat | `You'll use it to open ORVYN and approve payments. Avoid 123456 or your birthday.` |
| Sibuk | `Saving your PIN…` |
| Lewat 10 menit (server menolak) | Kembali ke Sign in dengan error di bawah Password: `That took too long. Sign in with your password again.` |
| Berhasil | Beranda, snackbar `Your PIN is changed` |

- HP tetap Registered Device. "Not you?" tetap melupakan HP seperti 0.8.1.
- Password salah dihitung (S1.5 AC3). Nasabah terkunci melihat layar terkunci.
- Font besar: kedua tombol teks terlihat (di area atas yang bisa digulir, 4.6).

---

## 9. E. Sistem visual dan aksesibilitas

### 9.1 Q-E1 Chip pilihan yang jelas di dua tema

Gaya di 4.9. Penerapan:

| Tempat | Komponen |
|---|---|
| Review Transfer `When` | Segmented `Send now` / `Schedule` |
| Pengulangan jadwal (`Once`, `Every {Sunday}`, `Every month on the {18th}`) | Chip pilih-satu |
| Valas | Segmented `Buy` / `Sell`, chip mata uang |
| Sheet deposito `At maturity` | Segmented `Renew` / `Pay out` |
| Opsi `At maturity` saat buka deposito | Tetap kartu radio 0.8.1 (dua baris dengan penjelasan, tidak cocok untuk segmented) |
| Pendaftaran (Work, income, reason) | Chip pilih-satu, minimal 48dp (dulu 44dp) |
| Riwayat | Filter chip (10.3), minimal 48dp (dulu 40dp) |
| Saran Vyna | Chip, minimal 48dp (dulu 44dp) |
| Grafik Fund | Chip `1 month` / `6 months` / `1 year` (11.8) |
| Quick Amount | Chip (4.7) |

Terang: tidak dipilih putih bergaris `#858FA3`, dipilih `#0032A0` dengan centang putih. Gelap: tidak dipilih `#0F1422` bergaris `#5D6884`, dipilih `#7FA2FF` dengan centang dan teks `#001A5C`. Yang dipilih selalu yang paling mencolok di dua tema (UI-10).

### 9.2 Q-E2 Satu jenis huruf dan kontras AA

- `textTertiary` terang `#616D86` (4.2, 3.3).
- Skala 15 peran (4.1). Layar yang dicek bebas Roboto: Wealth, Insights, investasi, deposito, valas, Vyna, kantong, kartu RM, pemilih tanggal.
- Nominal valas `JPY 1,000` memakai AmountText dengan `displaySmall` (sama dengan nominal Rupiah).
- Keterangan baris 14sp. 12sp hanya caption grafik.

### 9.3 Q-E3 Transisi layar

Seluruhnya di 4.3.

### 9.4 Q-E4 Ketukan ganda

Seluruhnya di 4.3 (penahan input) dan 4.4 (sekali tembak). Tempat yang diuji: `Send {Rp50,000}`, `Buy {Rp1,000,000}` (Bond), `Next` di buka deposito (tenor tidak terlewat), `Continue` di nominal Transfer, `Next` di semua alur dengan tombol bawah, dan mengetik PIN cepat.

### 9.5 Q-E5 Semua sakelar membacakan statusnya

SwitchRow (4.15) di semua sakelar. Contoh TalkBack: `Freeze card, Stops every payment until you unfreeze it, switch, on`.

### 9.6 Q-E6 Vyna

| Bagian | Desain |
|---|---|
| Streaming | Gelembung yang sedang menerima potongan tidak memakai live region. Status `Vyna is thinking…` diumumkan sekali saat mulai |
| Selesai | Seluruh jawaban diumumkan sekali (live region polite di wadah yang diisi saat streaming selesai) |
| Format | Hanya subset markdown: baris yang diawali `- `, `* `, atau `1. ` menjadi daftar dengan bullet `•` (atau nomor), indentasi gantung 16dp, jarak 4dp. `**teks**` menjadi berat 700. Simbol lain ditampilkan apa adanya. Tidak ada judul, tautan, atau tabel |
| Pembaca suara | Tombol ikon 48dp di top bar sebagai sakelar: nyala `ph_speaker_high`, mati `ph_speaker_slash` (baru). TalkBack: `Read replies aloud, switch, on/off` |
| Animasi "thinking" | Tiga titik berdenyut. Animasi mati: tiga titik diam dan teks `Vyna is thinking…` |
| Disclaimer (`labelMedium` `textTertiary` di bawah field) | `Vyna can get things wrong. Check the details before you send money.` |
| Saran | Chip 48dp (4.9) |

Terang dan gelap: gelembung nasabah `primaryContainer`, gelembung Vyna `surface` bergaris `outline` (0.8.1).

### 9.7 Q-E7 Label TalkBack dan tanda belum dibaca di inbox

| Tempat | Label atau aturan |
|---|---|
| Saldo tersembunyi (hero, kartu rekening, kantong, detail rekening) | `Balance hidden` |
| Ikon bagikan e-statement | `Share {September 2026} statement` |
| Baris inbox tanpa tautan | Teks saja, tanpa role, tidak diumumkan "disabled" |
| Baris inbox dengan tautan | Role Button, label `"{judul}, {isi}, {waktu}"`, ditambah `Unread` di depan bila belum dibaca |
| Judul belum dibaca | `titleMedium` berat 700, warna `text`, titik 8dp `primary` tetap |
| Judul sudah dibaca | Berat 600, warna `textSecondary` |

### 9.8 Q-E8 Baris yang bisa diketuk selalu membuka sesuatu

- Cards: baris pembayaran kartu terakhir membuka detail transaksi (`tx/{id}`).
- Wealth: **seluruh baris aset** bisa diketuk, dengan `ph_caret_right` 20dp `textSecondary` di kanan. Teks `Open` dihapus. `Savings and pockets` membuka detail rekening ORVYN Everyday (10.1, PO-16).
- Persen aset: teks dan label TalkBack sama. Di bawah 1%: `Less than 1% of assets`, selain itu `{12}% of assets`.
- Segmen grafik komposisi minimal 6dp lebar, termasuk aset 1%. Jarak antar segmen 2dp `surface` (aturan dataviz).
- `Grow your money` hanya menampilkan produk yang belum dimiliki. Bila semua sudah dimiliki, bagian ini tidak tampil.
- Baris kartu kredit di bagian Owed: `Not counted in your total` (pengganti `Not taken off your assets`).

### 9.9 Q-E9 Status permintaan uang

StatusChip (4.17) di daftar Requests (di bawah nominal, kanan), detail permintaan (di bawah judul), dan split bill (per orang). Status `Waiting` tidak lagi berwarna `warning`.

### 9.10 Q-E10 Ikon tagihan per jenis

| Jenis | Ikon (Phosphor Regular, 24dp di tile 40dp) |
|---|---|
| PLN | `ph_lightning` (tetap) |
| BPJS | `ph_first_aid` (baru) |
| Pulsa | `ph_device_mobile` (sudah ada) |
| E-wallet | `ph_wallet` (baru) |

Warna tile `primaryContainer`, ikon `onPrimaryContainer`, sama di dua tema.

### 9.11 Q-E11 Grafik Insights

- Batang bulan lain `chart.barMuted` (`#7088C4` terang, `#55689C` gelap). Batang terpilih `primary`. Ujung atas batang membulat 4dp, alas rata di garis dasar, jarak antar batang tetap.
- Validasi `dataviz` (`validate_palette.js`): pemisahan batang terpilih dan batang lain ΔE 24.0 (terang) dan 20.4 (gelap), lolos untuk penglihatan normal dan buta warna. Kontras terhadap kartu 3.50 dan 3.36, lolos 3:1. Cek pita kecerahan dan kroma gagal, tetapi cek itu hanya untuk palet kategori. Grafik ini satu seri dengan satu batang ditonjolkan, jadi warna redup memang sengaja abu-biru.
- Batang terpilih juga dibedakan tanpa warna: label nominal di atasnya dan label bulan tebal (0.8.1).
- Ringkasan TalkBack tetap (S2.9 AC4).
- Kategori tanpa pengeluaran bulan ini: `No {Food and drinks} spending this month.` (MessageState kosong, ikon `ph_tray`, tanpa aksi). Kalimat tentang kartu kredit dihapus.

### 9.12 Q-E12 Skeleton dan status bar

- Skeleton: 4.10.1.
- Tema terang: saat hero tergulir hilang, ikon status bar berubah gelap dan latar di belakang status bar adalah `background` halaman. Strip `HeroMid` dihapus. Tema gelap: ikon tetap terang. Saat hero terlihat lagi, ikon kembali terang.

### 9.13 Q-E13 Kartu satu gaya, nama panjang di bukti

- Kartu di Scheduled, Requests, Insights, Pockets lewat `OrvynCard`: garis 1dp `outline` MASTER (`outlineVariant` Compose), radius 14dp. `IconTile` radius 10dp (dulu 12dp).
- Gambar bukti (1080px): nama penerima dibungkus maksimal 2 baris lalu elipsis `…`. Gambar bukti lain tidak berubah.

---

## 10. F. Story lama yang dibangun sekarang

### 10.1 Q-F1 Detail rekening (S1.9)

**Masuk dari:** kartu ORVYN Everyday di beranda (seluruh kartu bisa diketuk, dengan `ph_caret_right` di samping nama; ikon salin di kartu tetap ada sebagai target terpisah) dan baris `Savings and pockets` di Wealth. Back kembali ke asal.

**Layar** (rute `account/{id}`, `FlowScreen` dengan daftar yang bisa digulir dan tarik untuk refresh):

```
←
ORVYN Everyday                      (headlineMedium, heading)
┌ OrvynCard ───────────────────────────────┐
│ Account holder      Raka Pratama          │
│ Account number      4068 2436 24          │
│ Balance             Rp12,450,000   [mata] │
│ Opened on 3 Mar 2024                       │
└───────────────────────────────────────────┘
[ Share account details ]   Primary
[ Copy number ]             Secondary
HISTORY                     (eyebrow)
baris transaksi (TransactionRow, dikelompokkan per hari)
```

| Bagian | Teks |
|---|---|
| Judul | Nama rekening, `ORVYN Everyday` |
| Label baris | `Account holder`, `Account number`, `Balance` |
| Tanggal | `Opened on {3 Mar 2024}` (`bodyMedium` `textSecondary`) |
| Tombol mata | `Show balance` / `Hide balance` (mengubah pilihan yang sama dengan beranda) |
| Tombol utama | `Share account details` |
| Teks yang dibagikan | `{Raka Pratama} · ORVYN Bank · {4068 2436 24}` |
| Tombol sekunder | `Copy number` (menyalin tanpa spasi) |
| Snackbar setelah salin | `Account number copied` |
| Eyebrow riwayat | `HISTORY` |

| State | Tampilan |
|---|---|
| Memuat | Skeleton kartu 180dp + dua blok tombol + 3 baris |
| Error | MessageState `Couldn't load your account` + `Try again` |
| Offline dengan cache | Banner status (4.13, varian detail rekening) di atas kartu. Share dan Copy tetap bekerja (nomor dari cache) |
| Riwayat kosong | `No transactions yet` / `Money you send and receive shows up here.` |
| Riwayat halaman berikutnya gagal | Baris `Couldn't load more. Try again` (10.3) |
| Saldo disembunyikan di beranda | `Rp••••••••`, TalkBack `Balance hidden` (PO-9) |

Font besar: label dan nilai bertumpuk (label di atas nilai) pada font ≥ 1,5. Saldo memakai 4.5. Terang dan gelap: token standar. TalkBack: setiap baris kartu satu node (`"Account number, 4068 2436 24"`, nomor dibaca per angka).

### 10.2 Q-F2 "Share receipt" dan tanggal di layar sukses

Seluruhnya di 4.8 (baris `Date`, `Share receipt`, urutan tombol, tabel per layar sukses).

### 10.3 Q-F3 Filter riwayat Date dan Type

**Layar:** History. Di bawah kotak pencarian, satu FlowRow filter chip (4.9):

`[Date ▾]  [Type ▾]  [Money in]  [Money out]`

- `Money in` dan `Money out` saling eksklusif: memilih satu melepas yang lain. Tidak memilih keduanya berarti semua arah. Chip `All` 0.8.1 dihapus.
- Saat ada filter aktif, baris filter diikuti tombol teks `Clear filters` 48dp.

**Sheet Date** (judul `Date`), pilihan radio satu baris 56dp:

| Pilihan | Label chip saat aktif |
|---|---|
| `Any time` (bawaan) | `Date` (tidak dipilih) |
| `This month` | `This month` |
| `Last month` | `Last month` |
| `Last 3 months` | `Last 3 months` |
| `Pick dates` → Material 3 `DateRangePicker` di dialog layar penuh, judul `Pick dates`, tombol `Save` dan `Cancel`. Tanggal akhir tidak bisa sebelum tanggal mulai (bawaan pemilih), tidak bisa setelah hari ini | `{3 Oct} to {16 Oct}`. Bila tahun berbeda dari tahun ini: `{3 Oct 2025} to {16 Jan 2026}` |

Memilih radio langsung menerapkan dan menutup sheet.

**Sheet Type** (judul `Type`), pilihan checkbox (multi):

| Pilihan | Jenis Journal Entry |
|---|---|
| `Transfers` | Transfer ORVYN, transfer bank lain, bayar Money Request |
| `QRIS` | Pembayaran QRIS |
| `Bills` | Bill Payment |
| `Cards` | Belanja kartu, bayar tagihan kartu |
| `Pockets` | Pindah uang kantong |
| `Wealth` | Valas, deposito (buka, cair, bunga), investasi, kupon obligasi |

Tombol: `Show results` (utama) dan `Clear` (teks). Label chip saat aktif: satu jenis = nama jenisnya (`QRIS`), dua atau lebih = `{2} types`.

| State | Tampilan |
|---|---|
| Hasil kosong dengan filter | MessageState: `Nothing matches` / `Try other dates or types, or clear the filters.` / tombol Secondary `Clear filters` |
| Tarik ke bawah | Memuat ulang dengan filter yang sama |
| Halaman berikutnya gagal | Baris terakhir 56dp, ikon `ph_warning_circle` + teks `Couldn't load more. Try again`, seluruh baris bisa diketuk (role Button). Saat diketuk: spinner menggantikan ikon |
| Memuat halaman berikutnya | Spinner 24dp di tengah baris terakhir |
| Error memuat awal | 6.3 |

Filter, pencarian, dan arah bisa digabung. Pilihan filter tetap saat kembali dari detail transaksi. TalkBack: chip `Date` dibaca `Date filter, Last month, selected`, sheet diumumkan dengan judulnya.

### 10.4 Q-F4 Nominal opsional di QR-ku (S1.16 AC1)

**Layar My QR:**

| Keadaan | Di bawah QR |
|---|---|
| Tanpa nominal | Baris akun 0.8.1, lalu tombol Secondary `Add an amount` |
| Dengan nominal | Nominal `{Rp50,000}` (`titleLarge`, tabular), lalu dua tombol teks berdampingan (bertumpuk di font besar): `Change amount` dan `Remove amount` |

**Layar nominal** (pola 4.7): judul `Add an amount`, kalimat `Whoever scans this QR pays this amount.`, tanpa baris "Available", Quick Amount bawaan (50k, 100k, 250k, 500k). Tombol: `Enter an amount` (0), `Minimum Rp10,000` (di bawah Rp10,000, PO-25), `Show QR for {Rp50,000}` (aktif). Back kembali ke My QR tanpa perubahan.

- `Remove amount` langsung menghapus (tidak destruktif, bisa ditambah lagi) dan QR kembali tanpa nominal.
- Saat QR dibuat ulang: skeleton kotak QR. Gagal: 6.3 (`Couldn't make your QR`).
- TalkBack gambar QR: `QR code for {Raka Pratama}` atau `QR code for {Raka Pratama}, {Rp50,000}`.
- Kecerahan layar tetap naik selama QR tampil (S1.16 AC3).

### 10.5 Q-F5 Pulsa: nomor sendiri, operator saat mengetik (S1.20)

**Layar:** Bills → Phone (langkah nomor).

| Bagian | Isi |
|---|---|
| Baris pertama (ListRow, di atas field) | Ikon `ph_device_mobile`, judul `My number`, subjudul `{0812 3456 7890} · {Telkomsel}`. Mengetuknya mengisi field dan langsung menjalankan cek seperti nomor `Paid before` |
| Field | `Mobile number`, keyboard angka |
| Helper (0 sampai 3 digit) | `For example 0812 3456 7890` |
| Helper (4 digit ke atas, dikenal) | `Operator: {Telkomsel}` |
| Error (4 digit ke atas, tidak dikenal) | `We don't know this operator. Check the first 4 digits.` `Continue` nonaktif |
| `Paid before` | Tetap 0.8.1 di bawah field |

Nomor sendiri diambil dari data Customer di server, tidak dari SIM. Bila belum ada (data belum dimuat), baris `My number` tidak tampil. Operator dicek di HP dengan tabel awalan yang sama dengan server (`operatorFor`). Helper operator memakai live region polite supaya TalkBack mengumumkannya sekali saat berubah.

### 10.6 Q-F6 Emas dalam gram, unit di detail investasi

**Layar beli Gold Savings** (pola 4.7):
- Di area atas, di bawah judul: segmented `Rupiah` / `Grams` (4.9). Bawaan `Rupiah`. Mengganti mode mengosongkan nominal.
- **Mode Rupiah:** sama dengan 0.8.1, termasuk Quick Amount dan `About {0.0333} g at {Rp1,500,000} a gram`.
- **Mode Grams:**

| Bagian | Isi |
|---|---|
| Nominal | `{0.05} g` (AmountText). Kosong: `0 g` berwarna `textTertiary` |
| Keypad | Tombol kiri bawah `.` (label TalkBack `Decimal point`) menggantikan `000`. Paling banyak 2 angka di belakang titik; angka ketiga diabaikan |
| Quick Amount | Tidak ada (PO-12) |
| Baris di bawah nominal | `You pay {Rp75,000}` (`titleMedium`), lalu `{Rp1,500,000} a gram` (`bodyMedium` `textSecondary`). Rupiah = gram × harga jual per gram, dibulatkan ke atas, sama dengan Journal Entry |
| Tombol aktif | `Buy {0.05} g for {Rp75,000}` |
| Tombol nonaktif | `Enter an amount` (0), `Minimum 0.01 g` (di bawah 0,01 g), `Minimum Rp50,000` (gram cukup tapi Rupiah di bawah Rp50,000) |
| PIN (kalimat) | `To buy {0.05} g of gold for {Rp75,000}.` |
| Teks sibuk | `Buying {0.05} g…` |
| Sukses | Headline `Bought`, nominal `{0.05} g`, subline `for {Rp75,000}`, baris Date, Price, Reference |

**Detail investasi**, kartu "You hold":

| Produk | Baris |
|---|---|
| Fund | `You hold` → `{1,234.5678} units`, lalu `Value today`, `You paid`, `Gain or loss` (0.8.1) |
| Gold Savings | `You hold` → `{0.0500} g`, lalu baris yang sama |
| Bond | Tetap 0.8.1 (`Face value`) |

### 10.7 Q-F7 Bayar permintaan uang lewat layar periksa (S2.6, K5)

**Alur:** Inbox (kartu permintaan) atau Requests → detail permintaan → `Pay {Rp50,000}` → layar periksa → PIN → sukses (4.8, dengan `Share receipt`). `Decline` → dialog → kembali ke detail dengan status `Declined`. Back dari periksa kembali ke detail.

**Detail permintaan** (rute `request/{id}`, judul `Money request`):

| Bagian | Isi |
|---|---|
| Kepala | `{Sinta Maharani} asks you` (0.8.1) dan StatusChip |
| Nominal | AmountText `{Rp50,000}` |
| Baris | `From` → `{Sinta Maharani}`, `Note` → `{catatan}` (bila ada), `Sent` → `{16 Oct 2026, 19:27 WIB}`, `Status` → StatusChip |
| Tombol (status To pay) | Utama `Pay {Rp50,000}`, di bawahnya Destructive text `Decline` |
| Status lain | Tanpa tombol Pay dan Decline (AC4) |

**Layar periksa:**

| Bagian | Isi |
|---|---|
| Judul | `Check the details` |
| Penerima | Header penerima seperti Transfer (inisial, nama, `ORVYN Bank · {nomor}`) |
| Baris | `Amount` → `{Rp50,000}`, `Fee` → `Free`, `Note` → `{catatan}`, garis, `Total` → `{Rp50,000}`, `Balance after` → `{Rp1,200,000}` |
| Tombol utama | `Pay {Rp50,000}` |
| Saldo kurang | Kotak error `You're {Rp20,000} short.` dan tombol nonaktif |
| PIN | `To pay {Rp50,000} to {Sinta Maharani}.` (0.8.1), sibuk `Paying {Rp50,000}…` |

**Dialog Decline** (4.16, PO-26):

| Bagian | Teks |
|---|---|
| Judul | `Decline this request?` |
| Kalimat | `{Sinta} gets a message that you declined.` |
| Konfirmasi (destruktif) | `Decline` |
| Batal | `Keep request` |

Kartu permintaan di inbox: role Button, label `"{Sinta Maharani} asks you for {Rp50,000}"`, membuka detail. Terang dan gelap: token standar.

### 10.8 Q-F8 Admin

Halaman admin di 12.4 (Transactions) dan 12.5 (Content → Banners). Settings tidak berubah (K4): tidak ada sakelar Widget. Aplikasi menampilkan banner aktif (tanggal mulai sudah lewat, tanggal berakhir belum) sesuai urutan admin.

---

## 11. G. Copy dan detail

### 11.1 Q-G1 Banner promo di bawah transaksi terakhir (K1)

**Urutan beranda:** hero → aksi cepat → (banner status bila offline atau gagal refresh) → kartu insight → `ACCOUNTS` → `WEALTH` → `YOUR RELATIONSHIP MANAGER` → `POCKETS` → `RECENT` → banner promo. Wealth dan RM tetap menempel ke rekening seperti 0.8.1.

Banner promo:
- Jarak atas 24dp setelah daftar Recent, tanpa eyebrow. Indikator titik di bawahnya (0.8.1).
- **Putar otomatis** setiap 6 detik hanya bila: TalkBack (touch exploration) mati, animasi tidak dimatikan, ada 2 banner atau lebih, dan tidak ada sentuhan di carousel dalam 6 detik terakhir. Saat jari menyentuh carousel, putar otomatis berhenti seketika (termasuk animasi geser yang sedang berjalan dibatalkan).
- **Dua target terpisah:** pil aksi (misalnya `Scan to pay`) langsung menjalankan aksinya. Bagian lain banner membuka sheet detail. Pil punya area sentuh minimal 48dp (visual 32dp).
- Ketukan selalu membuka banner yang sedang terlihat saat jari menyentuh.
- Tidak ada banner aktif: bagian ini tidak tampil dan tidak menyisakan ruang.
- TalkBack: banner role Button, label `"{judul}. {isi}"`, aksi klik `Read more`. Pil role Button dengan labelnya. Halaman carousel `"Banner {1} of {3}"`.

### 11.2 Q-G2 Pemindai QRIS

**Layar:** `Scan QRIS` (kamera penuh).

| Bagian | Isi |
|---|---|
| Top bar | Kiri `ph_x` (`Close`), tengah `Scan QRIS`, kanan tombol senter 48dp |
| Senter mati | Ikon `ph_flashlight` (Regular, baru), TalkBack `Turn on the flashlight` |
| Senter nyala | Ikon `ph_flashlight_fill` (Fill, baru), TalkBack `Turn off the flashlight` |
| HP tanpa senter | Tombol tidak tampil |
| Pesan error | Chip putih, radius pill, padding 12×8dp, ikon `ph_warning_circle` 20dp `#C62828`, teks `bodyMedium` `#0B1020`, di atas tombol bawah, live region polite. Teks error 0.8.1 (misalnya `We couldn't find a QR code in that picture.`) |
| Deskripsi kamera (TalkBack) | `Camera. Point it at a QRIS code.` |

Senter memakai `CameraControl.enableTorch`, mati saat pemindai ditutup atau aplikasi ke latar belakang. Ikon top bar putih di lingkaran Nero 40%, sama di dua tema.

### 11.3 Q-G3 Bantuan dan Privacy (S1.31, K3)

**Help (FAQ):**
- Setiap pertanyaan: baris minimal 56dp, judul `titleMedium`, `ph_caret_down` 20dp `textSecondary` di kanan, berputar 180° dalam 150ms saat terbuka (animasi mati: langsung). Jawaban `bodyLarge` `textSecondary` di bawahnya.
- TalkBack: role Button, `stateDescription` `Expanded` / `Collapsed`, aksi expand/collapse.
- Di bawah daftar (juga di bawah hasil pencarian kosong), kartu `OrvynCard`:

| Bagian | Teks |
|---|---|
| Ikon | `ph_sparkle` di IconTile |
| Judul | `Still need help?` |
| Kalimat | `Vyna can answer questions about your money and how ORVYN works.` |
| Tombol Secondary | `Ask Vyna` → membuka Vyna |

- Vyna dimatikan petugas: kartu tidak tampil (PO-17). Tidak ada nomor telepon dan tidak ada tombol dialer.

**Privacy:** dua baris berubah atau baru. Baris lain tetap 0.8.1 (What ORVYN sends, Fingerprint, Gallery, Never touched, No tracking, Notifications), dan bersama-sama mencakup semua baris PRD 7.4.

| Judul baris | Teks |
|---|---|
| `Camera` (berubah) | `Used only when you open it: to scan a QRIS code, and to photograph your KTP and take a selfie when you open an account. QR codes are read on your phone. The KTP photo and selfie stay on your phone, are never sent, and are deleted as soon as you move on.` |
| `Microphone` (baru, setelah Camera) | `Used only after you tap the microphone in Vyna. Your phone turns what you say into text, and only that text goes to Vyna. The recording never leaves your phone.` |

### 11.4 Q-G4 Format angka, tanggal, kurs

| Tempat | Sebelum | Sesudah |
|---|---|---|
| Periode tagihan PLN dan BPJS | `Bill for 2026-10` | `Bill for Oct 2026` |
| Persen dan nominal negatif | `-1.30%` | `−1.30%` (U+2212) |
| Eyebrow investasi | `YOUR INVESTMENTS · RP25,971,710` | Eyebrow `YOUR INVESTMENTS`, lalu baris `Total` → `Rp25,971,710` (`titleMedium`, tabular) di bawahnya |
| Kurs JPY (dan setiap mata uang yang kursnya di bawah Rp1,000) | `Rp108.025` | `100 JPY = Rp10,802.50`. Di tabel kurs: kolom mata uang `100 JPY`, nilai per 100 dengan 2 desimal |
| Catatan tabel kurs | `Rupiah per one unit (JPY: per one yen).` | `Rupiah for one unit. JPY is per 100 yen.` |

Kurs tersimpan dan hitungan tidak berubah. Aturan masuk ke MASTER §6.

### 11.5 Q-G5 Copy yang tepat

| Tempat | Teks |
|---|---|
| RTGS, baris `Arrives` di periksa dan subjudul opsi RTGS | `Within the hour` |
| Tombol layar tukar valas (PO-14), aktif | `Buy {USD 100.00}` / `Sell {USD 100.00}` (tetap langsung ke PIN) |
| Tombol valas nonaktif | `Enter an amount` (0), `Not enough {USD}` (jual lebih dari saldo valas) |
| Sign in, kalimat | `Use your phone number and ORVYN password.` |
| Dialog batal permintaan (PO-15) | Judul `Cancel this request?`, kalimat `{Sinta} won't be able to pay it anymore.`, konfirmasi destruktif `Cancel request`, batal `Keep request` |
| Unlock | Selalu `Welcome back, {nama depan}`. Nama depan disimpan di HP setiap kali masuk berhasil, jadi tersedia setelah keluar otomatis dan setelah aplikasi dibuka ulang |

### 11.6 Q-G6 Pencarian kosong dan minimum

- Pencarian penerima tanpa hasil: MessageState kosong, ikon `ph_magnifying_glass`, judul `No matches for "{zzz}"`, kalimat `Check the spelling, or send to a new recipient.`, tombol Secondary `New recipient`.
- Top up e-wallet: Rp0 → `Enter an amount`, Rp1 sampai Rp9,999 → tombol nonaktif `Minimum Rp10,000` (error inline 0.8.1 `The smallest top-up is Rp10,000.` tetap), Rp10,000 ke atas → `Pay {Rp11,000}`. Label `Choose what to pay` tetap untuk PLN token dan pulsa sebelum produk dipilih.

### 11.7 Q-G7 Back dari periksa yang disiapkan Vyna

Saat Vyna membuka layar periksa (transfer, tagihan, kantong), layar periksa dibuka sebagai satu tujuan di atas Vyna, tanpa langkah penerima dan nominal di tumpukan. Satu Back (panah atau sistem) kembali ke percakapan Vyna yang utuh. Bila nasabah mengetuk `Change` pada nominal di periksa, barulah langkah nominal dibuka, dan Back dari sana kembali ke periksa. Tidak ada copy baru.

### 11.8 Q-G8 Kurs dan grafik Fund

**Tabel kurs:** kolom `Currency`, `You sell at` (nilai Buy Rate), `You buy at` (nilai Sell Rate). Header kolom rata kanan, angka tabular.

**Grafik Fund** (detail produk), dicek dengan `dataviz`:
- Bentuk: garis (perubahan sepanjang waktu), satu seri, garis 2dp `primary`, tanpa area, grid horizontal tipis `outline` 1dp, tanpa sumbu Y berlabel.
- Chip rentang di atas grafik: `1 month`, `6 months`, `1 year` (4.9).
- Baris perubahan di atas grafik untuk rentang terpilih:

| Kasus | Tampilan | TalkBack |
|---|---|---|
| Naik | `ph_arrow_up` 16dp + `+{4.10}% in {6 months}`, warna `success` | `Up {4.10}% in {6 months}` |
| Turun | `ph_arrow_down` 16dp + `−{2.30}% in {1 month}`, warna `text` (tidak merah) | `Down {2.30}% in {1 month}` |
| Tetap | `No change in {1 year}`, warna `textSecondary`, tanpa panah | sama |

- Nilai tertinggi dan terendah: label `labelMedium` `textSecondary` di tepi kanan, sejajar tinggi titik tertinggi dan terendah, nilai saja (`{Rp1,284.5}`, format `formatRate`). Bila keduanya terlalu dekat (kurang dari 16dp), hanya nilai tertinggi yang tampil.
- Ketuk dan tahan grafik: garis bantu vertikal dan nilai di atas grafik (0.8.1).
- Ringkasan TalkBack 0.8.1 ditambah: `Highest {Rp1,284.5}, lowest {Rp1,201.2}.`
- Caption tanggal di bawah grafik: `bodySmall` (satu-satunya pemakaian 12sp yang sah).

### 11.9 Q-G9 Tidak ada field teks di layar keypad

**Alur minta uang baru:** pilih orang → nominal (4.7, tanpa field catatan, tombol `Continue`) → **periksa** → sukses `Request sent`. Back dari periksa kembali ke nominal.

**Layar periksa permintaan:**

| Bagian | Teks |
|---|---|
| Judul | `Check the request` |
| Baris | `Ask` → `{Sinta Maharani}`, `Amount` → `{Rp50,000}`, `Open for` → `7 days` |
| Field | `Note (optional)`, helper `For example "Lunch on Friday". {Sinta} sees this.` dan hitungan `{0}/50` |
| Tombol utama (menempel) | `Ask for {Rp50,000}` |
| Gagal | Kotak error di atas tombol (4.12) |

Split bill: field `What for (optional)` ada di layar `Check the shares` yang tidak punya keypad. Keypad untuk mengubah bagian ada di sheet terpisah. Ini sudah memenuhi aturan. Tidak ada layar lain dengan field teks dan keypad sekaligus.

### 11.10 Q-G10 Judul riwayat kantong

Di riwayat kantong dan detail transaksi kantong:

| Arah | Judul | Subjudul |
|---|---|---|
| Masuk ke kantong | `Added from ORVYN Everyday` | `{Pocket} · {14:05}` (0.8.1) |
| Keluar dari kantong | `Moved to ORVYN Everyday` | sama |
| Tabung otomatis | `Added from ORVYN Everyday` | `Auto-save · {14:05}` |

Judul dihitung saat dibaca, termasuk transaksi lama (PO-20).

### 11.11 Q-G11 Copy widget

| Keadaan | Teks |
|---|---|
| Tombol | `Show balance` |
| Saldo tampil | `As of {14:05} · hides in 10 seconds` |
| Tersembunyi | `Balance hidden` |

Tata letak widget tidak berubah. Font widget tetap bawaan sistem (RemoteViews).

---

## 12. Admin (web)

Semua halaman memakai komponen admin yang ada (`card`, `btn`, `btn secondary`, `btn danger`, `chip ok/bad`, `ok-box`, `hint`). Bahasa Inggris. Kotak error memakai gaya baru `err-box` (sama dengan `ok-box`, warna `--error` dan `--errorContainer`, ikon peringatan). Pesan sukses dan error tidak pernah membawa data rahasia lewat URL.

### 12.1 Registrations: penolakan wajib alasan (Q-C7)

| Bagian | Isi |
|---|---|
| Label field | `Reason (the applicant sees it)` (tetap) |
| Placeholder | `The name doesn't match the NIK.` (tetap) |
| Alasan kosong atau hanya spasi | Server menolak. Halaman kembali dengan `err-box` di kartu pendaftaran itu: `Write a reason first. The applicant sees it in the app.` Field mendapat fokus dan `aria-invalid="true"`. Pendaftaran tetap pending |
| Berhasil | `ok-box`: `Rejected. The applicant sees your reason in the app.` (tetap) |

### 12.2 Officers: password awal tampil sekali (Q-C8)

Setelah `Add officer`, halaman Officers menampilkan kotak hasil sekali (data diambil dari sesi petugas, tidak dari query string):

| Bagian | Isi |
|---|---|
| Judul kotak | `Officer added` |
| Baris | `Name: {Dewi Lestari}`, `Email: {email}`, `Starting password: {password}` (font mono) |
| Tombol | `Copy password` → teks berubah jadi `Copied` selama 2 detik |
| Catatan | `This is the only time it's shown. {Dewi} must change it at first sign-in.` |
| Muat ulang | Kotak tidak tampil lagi. Tidak ada pesan pengganti |
| Email sudah ada | `err-box`: `That email already has an officer account.` (tetap) |

URL setelah tambah adalah `/admin/officers` tanpa query. Password tidak tertulis di log atau Audit Entry.

### 12.3 System → Reset demo (Q-C3)

Formulir tetap. Berhasil walau ada passkey: `ok-box` `Demo reset. {3} customers removed. Create new testers from Customers.` (tetap). Salah konfirmasi: `Not reset: type RESET and your own password.` (tetap).

### 12.4 Transactions: filter jenis lengkap (Q-F8 AC1)

Dropdown `Kind` (pilihan pertama `All kinds`) dengan `<optgroup>`. Label yang sama dipakai di kolom tabel.

| Grup | Pilihan (label) | Jenis |
|---|---|---|
| Transfers | `ORVYN transfer` | `internal_transfer` |
| | `Transfer to another bank` | `external_transfer` |
| Payments | `QRIS payment` | `qris_payment` |
| | `Bill payment` | `bill_payment` |
| Cards | `Card spend` | `card_spend` |
| | `Credit card bill payment` | `card_payment` |
| Pockets | `Pocket move` | `pocket_move` |
| Wealth | `Foreign currency exchange` | `fx_exchange` |
| | `Time deposit opened` | `deposit_open` |
| | `Time deposit closed` | `deposit_close` |
| | `Time deposit interest` | `deposit_interest` |
| | `Investment buy or sell` | `investment` |
| | `Bond coupon` | `bond_coupon` |
| Admin | `Simulated money in` | `inflow` |
| | `Balance adjustment` | `adjustment` |
| | `Reversal` | `reversal` |
| | `Opening balance` | `opening` |

Test server memastikan setiap `kind` yang ada di `journal_entries` punya label di daftar ini.

### 12.5 Content → Banners: tanggal mulai, urutan, edit (Q-F8 AC2..AC5)

**Daftar banner** (urut: Live dulu menurut posisi, lalu Scheduled, lalu Ended):

```
[gambar]  Title
          One line under the title
          [Live] 1 Oct to 31 Oct 2026 · Position 1 · Button: Scan to pay     [Edit] [Remove]
```

| Status chip | Kapan |
|---|---|
| `Live` (`chip ok`) | Tanggal mulai sudah lewat, tanggal berakhir belum |
| `Scheduled` (chip netral) | Tanggal mulai belum tiba |
| `Ended` (chip netral, teks redup) | Tanggal berakhir sudah lewat |

**Formulir tambah dan edit** (satu formulir, mode edit terbuka di tempat formulir tambah dengan isi banner):

| Field | Label | Catatan |
|---|---|---|
| Judul | `Title` | tetap, maks 40 |
| Isi | `One line under the title` | tetap, maks 90 |
| Detail | `Shown when tapped` | tetap, maks 400 |
| Foto | `Photo` | tetap |
| Tombol | `Button` | tetap |
| Tanggal mulai (baru) | `Show from` | `type="date"`, bawaan hari ini |
| Tanggal berakhir | `Show until` | tetap |
| Urutan (baru) | `Position` | `type="number"`, min 1, helper `1 shows first.` |

| Mode | Judul formulir | Tombol |
|---|---|---|
| Tambah | `Add a banner` | `Add banner` |
| Edit | `Edit banner` | `Save changes` dan tautan `Cancel` |

| Hasil | Pesan |
|---|---|
| Tambah berhasil | `ok-box` `Banner added.` |
| Edit berhasil | `ok-box` `Banner saved.` |
| Hapus berhasil | `ok-box` `Banner removed.` |
| Tanggal berakhir sebelum mulai | `err-box` `Show until can't be before Show from.` Isi formulir tetap |
| Field wajib kosong | Validasi HTML bawaan (`required`) |

Setiap edit tercatat sebagai Audit Entry `banner.edit` dengan nilai sebelum dan sesudah. Migrasi: banner lama mendapat `Show from` = tanggal dibuat dan `Position` = urutan sekarang (PO-21).

---

## 13. Teks yang dikirim server (berubah)

| Tempat di server | Sebelum | Sesudah |
|---|---|---|
| `api.ts` `SIGN_IN_ERRORS.locked` | `Your account is locked after too many wrong tries. Ask the ORVYN team to unlock it.` | `Your account is locked. Ask the ORVYN team to unlock it.` (aplikasi 0.9.0 memakai copy 6.9; teks ini untuk APK 0.8.1 dan cadangan) |
| `cards.ts` PIN salah (detail kartu, bayar tagihan kartu) dan semua jalur PIN pembayaran | `Wrong PIN. Try again.` | `Wrong PIN. {N} tries left.` / `Wrong PIN. 1 try left.` |
| `transfers.ts` catatan metode RTGS | `Within the hour, for amounts above Rp100,000,000` | `Within the hour` |
| `POST /api/me/pin` PIN lama salah | `That's not your current PIN.` | `That's not your current PIN. {N} tries left.` / `... 1 try left.` |
| `POST /api/me/password` password lama salah | `That's not your current password.` (bila ada) | `That's not your current password. {N} tries left.` / `... 1 try left.` |
| Cek NIK sudah terdaftar | (baru) | `This NIK is already registered with ORVYN.` |
| Cek NIK ke-11 | (baru) | `Too many NIK checks for this sign-up. Start again from your phone number.` |
| PIN baru setelah Forgot PIN lewat 10 menit | (baru) | `That took too long. Sign in with your password again.` |
| Passkey yang sudah dihapus | (baru) | `This passkey doesn't work anymore. Sign in with your phone number and password.` |
| Periode tagihan PLN/BPJS | `2026-10` | `Oct 2026` (atau aplikasi yang memformat) |
| Judul riwayat kantong | `ORVYN Everyday` | `Added from ORVYN Everyday` / `Moved to ORVYN Everyday` |
| Admin reject tanpa alasan | (baru) | `Write a reason first. The applicant sees it in the app.` |

---

## 14. Ikon baru (Phosphor, vector drawable)

| Drawable | Berat | Dipakai di |
|---|---|---|
| `ph_wifi_slash` | Regular | Banner offline |
| `ph_lock_simple` | Regular | Layar terkunci |
| `ph_caret_down` | Regular | Filter chip, FAQ |
| `ph_speaker_slash` | Regular | Vyna pembaca suara mati |
| `ph_flashlight` | Regular | Senter mati |
| `ph_flashlight_fill` | Fill | Senter nyala (satu-satunya Fill di luar bottom nav, karena ini status nyala) |
| `ph_first_aid` | Regular | BPJS |
| `ph_wallet` | Regular | E-wallet |
| `ph_clock` | Regular | Status Waiting |
| `ph_prohibit` | Regular | Status Cancelled |

Ikon lain sudah ada di `res/drawable`. Tidak ada library ikon baru.

---

## 15. Catatan untuk PO (desain tetap ditulis dengan keputusan terbaik CX)

1. **Q-A1 AC4 "judul tampil satu baris".** Untuk nama sangat panjang ("Welcome back, {nama}"), judul mengecil sampai 18sp lalu membungkus ke baris kedua, tidak elipsis (4.6). Usul AC: "satu baris, kecuali nama yang tidak muat pada 18sp".
2. **Q-A5 AC1 "grid dua baris".** Di 360dp font 2,0 dengan 5 aksi, grid dua kolom menghasilkan tiga baris (2+2+1), karena tiga kolom 100dp memotong kata "Transfer". Di 411dp tetap dua baris (3+2). Usul AC: "grid tanpa memotong kata; dua baris di 411dp".
3. **Batas skala 1,5× untuk label bottom nav** (4.1). Q-A5 AC3 tetap terpenuhi (label terbaca utuh), tetapi teksnya berhenti membesar di 1,5×. Mohon dicatat sebagai keputusan desain.
4. **Q-B2 dan Q-B1 AC3** menyebut "layar periksa pencairan" yang di 0.8.1 belum ada. Desain menambahkan layar itu (6.2).
5. **Q-F3 Type.** Jenis `inflow`, `adjustment`, `opening`, dan `reversal` tidak masuk satu pun dari enam pilihan. Mereka tampil bila Type tidak difilter, dan `inflow` tertangkap filter `Money in`. Usul: pembatalan (reversal) ikut jenis transaksi yang dibatalkannya. Bila PO setuju, tambahkan ke AC.
6. **Q-D6 AC3** tidak menyebut konfirmasi setelah PIN baru dibuat. Desain memakai snackbar `Your PIN is changed` di beranda. Bila PO ingin layar sukses penuh seperti Change PIN, desainnya sudah ada di 7.1 dan bisa dipakai ulang.
7. **Q-B9 AC2** butuh endpoint ringan untuk mengecek status kunci tanpa PIN (untuk "Try again"). Ini keputusan teknis IT, desain tidak bergantung pada caranya.
8. **Q-B5 AC4** butuh server mengirim sisa percobaan di semua jalur PIN pembayaran (bagian 13, `cards.ts` saat ini `Wrong PIN. Try again.`).

---

## 16. Cek Gerbang 2

- [x] Setiap story Q-A1..Q-G11 punya bagian (tabel jejak di bagian 2).
- [x] Setiap layar yang berubah punya state: default, memuat, kosong, error, offline, sibuk, sukses, atau ditulis "tidak berlaku" bila memang tidak ada.
- [x] Copy final bahasa Inggris untuk semua yang tertulis "sesuai `design.md`" di `stories.md`: Q-B2 (6.2), Q-B4 AC3 (6.4), Q-B9 AC1 (6.9), Q-C1 AC1 (7.1), Q-C2 AC4 (7.2), Q-C3 AC5 (7.3), Q-C7 AC3 (12.1), Q-D2 AC5 (8.2), Q-E7 AC2 (9.7), Q-F3 AC6 (10.3), Q-F4 AC1 (10.4), Q-F6 AC3 (10.6), Q-F7 AC3 (10.7), Q-G3 AC5 (11.3), Q-G4 AC3 (11.4), Q-G5 AC1..AC3 (11.5), Q-G6 AC1 (11.6).
- [x] Copy dari `ui-audit.md` dipakai apa adanya. Copy baru sudah melewati `humanizer`: kalimat pendek, kata sehari-hari, tanpa tanda pisah panjang, tanpa kosakata khas AI.
- [x] Terang dan gelap untuk setiap komponen, kontras dihitung (3.3).
- [x] Aksesibilitas: label, role, dan state TalkBack, target 48dp, warna tidak pernah satu-satunya penanda, uji 200% di 360×640dp.
- [x] Konsisten dengan `design-system/MASTER.md`, dan MASTER sudah diperbarui (3.1).
- [x] Grafik dicek dengan `dataviz` dan warna batang divalidasi dengan skripnya (9.11, 11.8).
- [x] Tidak ada izin Android baru, tidak ada data HP baru, tidak ada nomor HP atau email asli.
