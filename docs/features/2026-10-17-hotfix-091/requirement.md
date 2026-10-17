# Requirement: hotfix 0.9.1 (sisa temuan QA quality pass)

Sumber: QA putaran 2 quality pass 0.9.0 (`docs/features/2026-10-16-quality-pass/qa-report.md`, temuan Q-02-F1..F5). 0.9.0 sudah dirilis 17 Okt dengan putusan READY FOR RELEASE. Lima temuan ini minor/polish, tidak ada yang membuat uang salah.

## Kata-kata tim bisnis

> "make sure ga ada defect atau bugs." (16 Okt) dan "kamu decide saja yg kamu rekomendasikan."
> "klo udah ready mah rilis aja ga perlu persetujuan saya" (17 Okt)

Jadi sisa temuan ini ikut ditutup, keputusan diambil orkestrator, dan 0.9.1 dirilis begitu QA READY.

## Temuan dan keputusan orkestrator

| Temuan | Masalah | Keputusan |
|---|---|---|
| Q-02-F2 | Minta uang dan bagian split Rp1,000–Rp9,999 bisa dibuat tapi tidak bisa dibayar (minimum transfer Rp10,000) | **Satu minimum:** permintaan uang dan setiap bagian split minimal sama dengan minimum transfer (setting yang sama, sekarang Rp10,000). Server menolak saat membuat; aplikasi menjelaskan minimum di tombol dan di sheet ubah bagian. Di produksi tidak ada permintaan terbuka di bawah Rp10,000, jadi tidak perlu migrasi data |
| Q-02-F3 | Total kategori Insights termasuk belanja kartu kredit, tapi detail kategori hanya menampilkan tabungan | **Detail ikut menampilkan transaksi kartu kredit**, dengan penanda sumbernya, supaya angka di baris dan isi detail selalu cocok |
| Q-02-F4 | Admin "Mature now" / job jatuh tempo dengan bunga Rp0 dan Pay out gagal HTTP 500; satu deposito gagal menahan deposito lain di menit yang sama | Bunga Rp0 tidak menulis posting beban/pajak (seperti jalur rollover). Job jatuh tempo memproses setiap deposito dalam transaksinya sendiri dan satu kegagalan tidak menahan yang lain |
| Q-02-F1 | Done/Enter di keyboard tidak menjalankan tombol utama di luar pendaftaran (misal New recipient, nomor meter PLN) | Terapkan aturan MASTER Text field di semua field: Next pindah field, Done di field terakhir menjalankan tombol utama bila aktif |
| Q-02-F5 | Snackbar beranda sedikit menutupi tombol QRIS yang menonjol | Snackbar di beranda berada di atas tombol QRIS, tidak menutupi apa pun |

## Yang dianggap berhasil

- Kelima temuan tertutup dan diverifikasi QA; tidak ada regresi; buku besar Balanced.
- Tidak ada izin Android baru, tidak ada data perangkat (ADR 0002), uang hanya lewat Journal Entry (ADR 0001).

## Batasan

- Hotfix kecil, versi **0.9.1**, versionCode 13.
- Server 0.9.1 tetap melayani APK 0.9.0 dan 0.8.1.
- Copy aplikasi bahasa Inggris.
