# Requirement: hotfix tanggal lahir dan pills nominal (0.8.1)

Sumber: tim bisnis, 16 Okt 2026. Dua bug ditemukan sendiri oleh tim bisnis di produksi (0.8.0).

## Kata-kata tim bisnis

> "saya ketemu 2 bugs nyata di production: 1. di proses onboarding, ada field tanggal lahir dengan format dd/mm/yyyy, tapi user tidak memungkinkan mengisi sesuai dengan format. 2. misal user ingin buka deposito, minimal sesuai ketentuan adalah 1 juta. tapi pills nominal malah mulai dari angka yg lebih kecil."

## Temuan orkestrator (penyebab)

1. **Tanggal lahir** (`ui/register/RegisterFlow.kt`, langkah data diri saat buka rekening): field memakai keyboard angka, tapi validasinya mewajibkan "/". Keyboard angka di Samsung dan Gboard tidak punya tombol "/", jadi format DD/MM/YYYY tidak bisa diketik dan pendaftaran macet di langkah ini.
2. **Pills nominal** (`AmountDisplay` di `ui/components/Fields.kt`): semua layar nominal memakai pills tetap 50k, 100k, 250k, 500k. Akibatnya:
   - Deposito (minimal Rp1.000.000): semua pill di bawah minimum.
   - Reksadana (minimal Rp100.000): pill 50k di bawah minimum.
   - SBN (minimal Rp1.000.000, kelipatan Rp1.000.000): semua pill tidak valid.
   - Layar lain (transfer, QRIS, tagihan, kantong, minta uang, bayar kartu, anggaran, emas) tidak bermasalah dengan minimumnya, tapi perlu dicek ulang.

## Keputusan tim bisnis (jawaban klarifikasi)

1. Tanggal lahir: **user cukup mengetik 8 angka, garis miring muncul otomatis** (17081990 tampil sebagai 17/08/1990). Bukan kalender.
2. Pills deposito: **1jt, 5jt, 10jt, 50jt**.
3. Masalah yang sama di investasi **ikut diperbaiki**: setiap layar nominal hanya menampilkan pills yang valid untuk aturan produknya.
4. Setelah QA menyatakan READY FOR RELEASE, orkestrator **boleh langsung merilis 0.8.1**.

## Yang dianggap berhasil

- Calon nasabah bisa mengisi tanggal lahir dengan keyboard angka bawaan HP dan lanjut ke langkah berikutnya.
- Tidak ada pill di layar mana pun yang nilainya ditolak oleh aturan produk di layar itu.

## Batasan

- Hotfix kecil: tidak ada fitur baru, tidak ada perubahan server kecuali memang diperlukan.
- Tidak ada izin Android baru (ADR 0002).
- Copy aplikasi tetap bahasa Inggris.
