# Desain: Hotfix 0.8.1, tanggal lahir dan Quick Amount

Sumber: `prd.md`, `stories.md` (H1 sampai H5), `design-system/MASTER.md`. Hotfix pada layar yang sudah ada: tidak ada layar baru, alur baru, token warna baru, atau izin baru. Copy di dokumen ini adalah teks final. IT menyalinnya persis.

Tidak ada copy baru yang terlihat di layar kecuali label Quick Amount juta (bagian 2.2). Semua teks lain tetap teks 0.8.0. Teks sudah dicek dengan skill humanizer: polos, tanpa tanda pisah panjang.

---

## 1. Field "Date of birth" (H1, H2)

**Tempat:** pendaftaran, langkah "About you" (`AboutStep` di `ui/register/RegisterFlow.kt`), field ketiga setelah "Full name" dan "NIK". Masuk dan kembali tetap seperti 0.8.0: tombol kembali di header membawa ke langkah sebelumnya, Continue membawa ke langkah berikutnya.

### 1.1 Tampilan dan copy

Komponen tetap `OrvynTextField`: label di atas, field outlined, helper atau error di bawah. Tidak ada placeholder, tidak ada ikon kalender.

| Bagian | Teks (persis) | Catatan |
|---|---|---|
| Label | `Date of birth` | tidak berubah |
| Helper | `DD/MM/YYYY` | tidak berubah, tampil selama tidak ada error |
| Error, tanggal belum lengkap atau tidak ada di kalender | `Use the format DD/MM/YYYY.` | tidak berubah |
| Error, umur di bawah 17 | `You need to be at least 17 to open an account.` | tidak berubah |
| Keyboard | angka (`KeyboardType.Number`) | tidak berubah |

Error tetap muncul hanya setelah Continue ditekan, lalu ikut berubah saat field diedit (perilaku 0.8.0). Alasan tidak mengganti teks error: tim bisnis meminta teks lama dipakai, dan teks itu sudah memberi tahu cara memperbaikinya.

### 1.2 Cara kerja input

Prinsip: **yang disimpan hanya angka (maksimal 8). Garis miring hanya tampilan**, dibuat oleh `VisualTransformation` dengan `OffsetMapping`. Garis miring tidak pernah ada di nilai field, jadi tombol hapus dan kursor bekerja per angka tanpa kode khusus.

| Aturan | Perilaku |
|---|---|
| Isi yang diterima | Setiap perubahan disaring menjadi angka saja (`it.filter(Char::isDigit)`). Huruf, spasi, "/", "-", "." yang ikut masuk dibuang. |
| Garis miring | Tampil tepat setelah angka ke-2 dan ke-4, segera setelah angka itu diketik: `1` → `1`, `17` → `17/`, `170` → `17/0`, `1708` → `17/08/`, `17081990` → `17/08/1990`. |
| Batas 8 angka | Bila hasil saringan 8 angka atau kurang, diterima. Bila lebih dari 8: kalau field sudah berisi 8 angka, perubahan diabaikan (angka ke-9 tidak masuk, di mana pun kursornya); kalau belum penuh (tempel teks panjang), ambil 8 angka pertama. |
| Hapus | Satu tekan hapus menghilangkan satu angka. Dari `17/` satu hapus menjadi `1` (garis miring ikut hilang karena hanya tampilan). Dari `17/08/1990` sepuluh kali hapus tidak diperlukan: delapan kali cukup untuk mengosongkan field. |
| Kursor di tengah | Kursor bisa ditaruh di mana saja. Kursor yang jatuh tepat setelah "/" dianggap berada sebelum angka berikutnya. Contoh H1 AC4: dari `17/08/1990`, hapus `08` lalu ketik `09` menjadi `17/09/1990`. |
| Tempel | `17/08/1990`, `17081990`, `17-08-1990`, dan `17.08.1990` semuanya tampil `17/08/1990`. |
| Kembali ke langkah ini | Tanggal tersimpan (ISO `1990-08-17`) diisi ulang sebagai angka `17081990`, jadi tampil `17/08/1990` dan Continue bisa langsung ditekan. |
| Membaca tanggal | Hanya bila tepat 8 angka: hari = angka 1-2, bulan = 3-4, tahun = 5-8, lalu `LocalDate.of(tahun, bulan, hari)`. Kurang dari 8 angka atau tanggal tidak ada (misalnya `31021990`) berarti `null` dan memunculkan "Use the format DD/MM/YYYY.". |
| Yang dikirim ke server | Tetap tanggal ISO `YYYY-MM-DD` (`date.toString()`), tidak berubah. |

Pemetaan kursor untuk IT (n = jumlah angka, o = posisi di angka, t = posisi di teks tampil):
- angka ke tampil: `t = o + (o >= 2 && n >= 2 ? 1 : 0) + (o >= 4 && n >= 4 ? 1 : 0)`
- tampil ke angka: `o = t - (t > 2 && n >= 2 ? 1 : 0) - (t > 5 && n >= 4 ? 1 : 0)`, lalu dibatasi ke `0..n`

`OrvynTextField` perlu parameter `visualTransformation` (default `VisualTransformation.None`). Field password tetap memakai transformasinya sendiri.

### 1.3 State

| State | Tampilan |
|---|---|
| Kosong | Field kosong, helper `DD/MM/YYYY` |
| Sedang diisi | Angka dengan garis miring otomatis, helper tetap |
| Lengkap dan valid | `17/08/1990`, helper tetap |
| Error format | Border dan teks `error`, ikon `ph_warning_circle` 16dp, teks `Use the format DD/MM/YYYY.` (menggantikan helper) |
| Error umur | Sama, teks `You need to be at least 17 to open an account.` |
| Loading, kosong data, offline | Tidak berlaku. Field ini tidak memanggil server. Perilaku kirim pendaftaran tidak berubah. |

### 1.4 Aksesibilitas

- TalkBack membaca label `Date of birth`, lalu isi field apa adanya beserta garis miringnya, misalnya "17/08/1990, edit box" (TalkBack bahasa Inggris mengucapkannya "17 slash 08 slash 1990"), lalu helper `DD/MM/YYYY`. Urutan baca sama dengan urutan visual. Tidak ada label TalkBack tambahan: mengganti bacaan field yang sedang diedit membuat umpan balik ketik per angka jadi membingungkan.
- Saat mengetik, TalkBack menyebut setiap angka yang masuk (perilaku bawaan). Garis miring tidak diucapkan sebagai ketikan karena bukan bagian dari nilai.
- Error tetap memakai `semantics { error(...) }` yang sudah ada, jadi TalkBack membaca "Error, Use the format DD/MM/YYYY.".
- Arti error tidak hanya dari warna: ada ikon dan teks.
- Field tetap setinggi minimal 56dp (OutlinedTextField). Di ukuran huruf 200%, `17/08/1990` (10 karakter, `bodyLarge`) muat di lebar 360dp. Helper dan error membungkus ke baris berikutnya, tidak terpotong.

### 1.5 Terang dan gelap

Tidak ada perubahan warna. Field memakai token yang sudah ada (`surface`, `outline`, `primary` saat fokus, `error`).

---

## 2. Quick Amount (H3, H4, H5)

### 2.1 Nilai per layar

`AmountDisplay` mendapat daftar nilai dari layar pemanggil. Bila tidak diberi, tetap 50k, 100k, 250k, 500k.

| Layar | Nilai (Rp) | Label chip | Alasan |
|---|---|---|---|
| Open a deposit (`Deposits.kt`) | 1,000,000 / 5,000,000 / 10,000,000 / 50,000,000 | `1M` `5M` `10M` `50M` | keputusan tim bisnis, min Rp1,000,000 |
| Beli dan jual Fund (`Invest.kt`, `p.kind == "fund"`) | 100,000 / 250,000 / 500,000 / 1,000,000 | `100k` `250k` `500k` `1M` | semua ≥ minimum Fund Rp100,000. Langkah yang dikenal pemula reksadana, naik sampai 1 juta. |
| Beli Bond (`Invest.kt`, `p.kind == "bond"`) | 1,000,000 / 2,000,000 / 5,000,000 / 10,000,000 | `1M` `2M` `5M` `10M` | semua ≥ Rp1,000,000 dan kelipatan Rp1,000,000 |
| Beli dan jual Gold Savings (`p.kind == "gold"`) | 50,000 / 100,000 / 250,000 / 500,000 | `50k` `100k` `250k` `500k` | tidak berubah |
| Semua layar lain (Transfer, QRIS, e-wallet, Pocket, Request, Split bill, Card "Another amount", Budget) | 50,000 / 100,000 / 250,000 / 500,000 | `50k` `100k` `250k` `500k` | tidak berubah |

Urutan selalu dari kecil ke besar, kiri ke kanan. Di layar jual Fund, nilai yang lebih besar dari nilai Holding tetap dipotong ke nilai Holding seperti 0.8.0 (logika `minOf(it, h.value)` tidak diubah). Bond tidak punya layar jual.

### 2.2 Aturan label

- Di bawah Rp1,000,000: ribuan + `k`. `50000` → `50k`, `250000` → `250k`.
- Rp1,000,000 ke atas: jutaan + `M` (huruf besar, tanpa spasi). `1000000` → `1M`, `50000000` → `50M`.
- Semua nilai di tabel 2.1 bulat ribuan atau bulat jutaan, jadi tidak ada desimal. Nilai Quick Amount baru di masa depan harus mengikuti ini (jangan `1.5M`).

Mengapa `M`, bukan `jt` atau `mn`: aplikasi berbahasa Inggris dan chip yang ada memakai `k`, jadi pasangannya `M` (singkatan Inggris yang umum untuk juta). `jt` adalah bahasa Indonesia. `1000k` sulit dibaca. Di halaman produk ada chip rentang grafik `1M` yang berarti 1 bulan, tapi itu layar lain tanpa angka Rupiah di dekatnya. Di layar nominal, chip berada tepat di bawah angka Rupiah besar yang langsung berubah saat chip diketuk, jadi artinya jelas.

### 2.3 Tata letak dan ukuran huruf terbesar

- Baris chip berubah dari `Row` menjadi `FlowRow` dengan jarak 8dp ke samping dan 8dp antarbaris, rata tengah (`Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`), lebar penuh.
- Di ukuran huruf normal keempat chip muat satu baris di 360dp (lebar konten 320dp setelah padding layar 20dp kiri dan kanan).
- Di ukuran huruf 200% (`labelMedium` 12sp menjadi sekitar 24sp) keempat chip tidak muat satu baris, lalu turun ke baris kedua (misalnya 3 + 1 atau 2 + 2), masing-masing rata tengah. Tidak ada chip yang terpotong, menyusut, atau keluar layar, dan tidak ada scroll ke samping. Ini juga memperbaiki baris 50k sampai 500k yang hari ini sudah keluar layar di 200%.
- Chip: tinggi `heightIn(min = 48.dp)` (bukan `height(48.dp)` tetap) dan lebar minimal 48dp, supaya teks tidak terpotong di huruf besar dan chip pendek seperti `1M` tetap target sentuh 48 × 48dp. Padding samping tetap 16dp. Di ukuran normal tampilan chip yang ada tidak berubah.
- Bila layar nominal kehabisan tinggi di 200% (layar ini tidak bisa di-scroll), `Spacer(weight)` di antara chip dan keypad yang menyusut lebih dulu. Chip tetap di atas dan terbaca utuh. Keypad yang terdorong di layar sangat pendek sudah ada sejak 0.8.0 dan di luar lingkup hotfix ini.

### 2.4 State chip

| State | Tampilan |
|---|---|
| Tidak terpilih | `primaryContainer` dengan teks `onPrimaryContainer` |
| Terpilih (nominal sama persis dengan nilai chip) | `primary` dengan teks `onPrimary` |
| Ditekan | ripple bawaan `clickable` |

Tidak ada state loading, kosong, error, atau offline pada chip. Error nominal tetap tampil di teks error `AmountDisplay` yang sudah ada (misalnya `That's more than you owe (…)`), tidak berubah.

Arti "terpilih" tidak hanya dari warna: angka Rupiah besar di atas chip menampilkan nominal yang sama, dan TalkBack menyebut "Selected" (2.5).

### 2.5 Aksesibilitas

- Label TalkBack setiap Quick Amount adalah nominal penuh dengan `Money.format(v)`, bukan teks chip: `Rp50,000`, `Rp100,000`, `Rp250,000`, `Rp500,000`, `Rp1,000,000`, `Rp2,000,000`, `Rp5,000,000`, `Rp10,000,000`, `Rp50,000,000`. Ini berlaku di semua layar (TalkBack hari ini membaca `50k` sebagai "fifty k").
- Chip yang terpilih diumumkan sebagai terpilih (`semantics { selected = … }`), jadi TalkBack membaca misalnya "Rp1,000,000, selected, double-tap to activate". Komponen `Chip` mendapat dua tambahan: parameter label TalkBack opsional (`contentDescription`) dan state `selected` di semantics. Chip lain yang memakai komponen ini (tenor deposito, Renew dan Pay out, rentang grafik) ikut mendapat state terpilih, tanpa perubahan tampilan.
- Urutan baca: angka Rupiah besar, pesan error atau "Available …", lalu chip dari kiri ke kanan, baris demi baris.
- Target sentuh minimal 48 × 48dp dengan jarak 8dp (2.3).

### 2.6 Terang dan gelap

Warna chip yang ada sudah memenuhi AA di kedua tema. Tidak perlu token baru.

| Pasangan | Terang | Gelap |
|---|---|---|
| Teks chip tidak terpilih (`onPrimaryContainer` di `primaryContainer`) | 13.6:1 | 8.5:1 |
| Teks chip terpilih (`onPrimary` di `primary`) | 10.69:1 | 6.54:1 |
| Terpilih dibanding tidak terpilih (`primary` dibanding `primaryContainer`) | 9.02:1 | 4.34:1 |

---

## 3. Cek untuk Gerbang 2 dan QA

- [ ] Ketik `17081990` dengan Samsung Keyboard tanpa "/" tampil `17/08/1990`. Angka ke-9 tidak masuk.
- [ ] Hapus dari `17/08/1990` sampai kosong: 8 kali tekan, satu angka per tekan.
- [ ] Tempel `17/08/1990` dan `17081990` tampil `17/08/1990`.
- [ ] Teks label, helper, dan dua error sama persis dengan 1.1.
- [ ] Label dan nilai chip per layar sama persis dengan 2.1.
- [ ] Huruf terbesar di 360dp, terang dan gelap: chip turun baris, tidak ada yang terpotong.
- [ ] TalkBack membaca chip sebagai nominal penuh (`Rp1,000,000`) dan menyebut "selected" pada chip terpilih.
