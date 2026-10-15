# User Stories ORVYN: Fase 3 "bank yang menumbuhkan"

Sumber: `PRD-ORVYN.md` (F18–F23, KR7), istilah dari `CONTEXT.md`, keputusan di `docs/adr/0003-holdings-outside-the-ledger.md`.
**Definisi selesai:** sama dengan Fase 1 dan 2. Setiap fitur bisa dimatikan dari Settings admin.

**Urutan pengerjaan:** valas, deposito, investasi (profil risiko, reksadana, SBN, emas), total aset, Prestige dan RM, passkey, lalu dokumentasi portofolio.

---

## F19. Valas

### S3.1 Rekening valas
**Sebagai** nasabah, **saya ingin** punya rekening dolar, yen atau euro, **supaya** saya bisa menyiapkan uang untuk liburan atau sekolah anak.

1. Nasabah bisa membuka rekening USD, SGD, JPY, AUD atau EUR, masing-masing satu, tanpa biaya.
2. Saldo ditampilkan dalam mata uangnya sendiri dengan pecahan yang benar (USD 2 desimal, JPY tanpa desimal), plus perkiraan nilai dalam Rupiah.
3. Rekening valas tidak punya nomor rekening dan tidak bisa menerima transfer dari luar.
4. Rekening valas bisa ditutup jika saldonya nol.

### S3.2 Tukar mata uang
**Sebagai** nasabah, **saya ingin** membeli dan menjual valas dengan kurs yang jelas, **supaya** saya tahu persis berapa yang saya dapat.

1. Layar tukar menampilkan kurs beli dan jual, jumlah yang dibayar, dan jumlah yang diterima, sebelum PIN.
2. Penukaran memakai PIN dan tercatat sebagai satu Journal Entry dengan FX Position di setiap mata uang. Pengecekan buku besar tetap lolos.
3. Minimal penukaran setara Rp100.000. Karena uang tetap milik nasabah sendiri, penukaran tidak dihitung dalam batas harian.
4. Nasabah Prestige dan Private mendapat selisih kurs lebih tipis, dan layar menyebutkannya.
5. Petugas mengatur kurs beli dan jual per mata uang di admin, dan setiap perubahan tercatat di audit log.

---

## F20. Deposito online

### S3.3 Membuka deposito
**Sebagai** nasabah, **saya ingin** membuka deposito dari aplikasi, **supaya** uang menganggur dapat bunga lebih tinggi.

1. Nasabah memilih nominal (minimal Rp1.000.000), tenor 1, 3, 6 atau 12 bulan, dan instruksi jatuh tempo: diperpanjang otomatis atau dicairkan ke tabungan.
2. Layar menampilkan bunga per tahun, perkiraan bunga bersih setelah pajak 20%, dan tanggal jatuh tempo, sebelum PIN.
3. Suku bunga per tenor diatur petugas. Deposito yang sudah dibuka memakai bunga saat dibuka.
4. Deposito tampil di beranda dan di layar Wealth.

### S3.4 Jatuh tempo dan pencairan awal
**Sebagai** nasabah, **saya ingin** deposito saya berjalan sendiri saat jatuh tempo, **supaya** saya tidak perlu ingat tanggalnya.

1. Saat jatuh tempo, bunga bersih dibayar dari House Account bunga. Jika diperpanjang, pokok dan bunga menjadi deposito baru dengan tenor yang sama. Jika dicairkan, semuanya masuk ke tabungan.
2. Nasabah mendapat push dan pesan di kotak masuk.
3. Nasabah bisa mencairkan lebih awal dengan PIN. Bunga hangus dan aplikasi menjelaskannya sebelum PIN.
4. Petugas bisa menjalankan "Mature now" untuk demo, tanpa menunggu berbulan-bulan.

---

## F21. Investasi

### S3.5 Profil risiko
**Sebagai** nasabah, **saya ingin** tahu profil risiko saya, **supaya** saya memilih investasi yang cocok.

1. Kuis 5 pertanyaan pilihan ganda menghasilkan Conservative, Moderate atau Aggressive, dengan penjelasan singkat.
2. Kuis wajib diisi sebelum pembelian investasi pertama, dan bisa diulang kapan saja.
3. Membeli produk di atas profil risiko diperbolehkan setelah nasabah mencentang pernyataan bahwa ia memahami risikonya.

### S3.6 Reksadana
**Sebagai** nasabah, **saya ingin** membeli dan menjual reksadana, **supaya** uang saya bertumbuh.

1. Daftar reksadana menampilkan jenis, tingkat risiko, NAB per unit hari ini, dan kinerja 1 tahun.
2. Detail reksadana menampilkan grafik NAB (1 bulan, 6 bulan, 1 tahun) dan keterangan manajer investasi fiktif.
3. NAB berubah setiap hari secara simulasi, berbeda per jenis (pasar uang hampir datar, saham naik turun lebih tajam).
4. Pembelian minimal Rp100.000, memakai PIN, dan unit dihitung dari NAB hari ini. Penjualan bisa sebagian atau semua unit, dan uang masuk ke tabungan saat itu juga.
5. Holding menampilkan unit, nilai hari ini, modal, dan untung atau rugi dalam Rupiah dan persen.

### S3.7 SBN ritel
**Sebagai** nasabah, **saya ingin** membeli obligasi negara ritel, **supaya** saya dapat kupon tetap setiap bulan.

1. Daftar SBN menampilkan seri fiktif, kupon per tahun, jatuh tempo, dan minimum pembelian.
2. Pembelian kelipatan Rp1.000.000 dengan PIN.
3. Kupon dibayar setiap tanggal 10 ke tabungan, setelah pajak 10%, dan nasabah mendapat push.
4. SBN dipegang sampai jatuh tempo. Pada jatuh tempo, nilai pokok kembali ke tabungan.

### S3.8 Tabungan emas
**Sebagai** nasabah, **saya ingin** menabung emas dalam gram, **supaya** saya bisa mulai dari nominal kecil.

1. Harga beli dan jual per gram diatur petugas dan tampil di layar.
2. Nasabah membeli dalam Rupiah (minimal Rp50.000) atau gram (minimal 0,01 g), dan menjual sebagian atau semua gram, dengan PIN.
3. Holding menampilkan gram, nilai jual hari ini, dan untung atau rugi.

---

## F22. Total aset

### S3.9 Layar Wealth
**Sebagai** nasabah, **saya ingin** melihat semua kekayaan saya dalam satu angka, **supaya** saya tahu posisi keuangan saya.

1. Satu angka Total Aset dalam Rupiah: tabungan, kantong, deposito, valas (kurs beli), dan investasi (nilai hari ini).
2. Grafik komposisi aset per jenis, dengan label dan persen sehingga tidak bergantung pada warna saja.
3. Tagihan kartu kredit tampil di bawahnya sebagai kewajiban, tidak dikurangkan dari total aset.
4. Setiap jenis aset bisa diketuk untuk membuka layarnya.

---

## F18. Tier Prestige

### S3.10 Tampilan Prestige
**Sebagai** nasabah Prestige, **saya ingin** aplikasi terasa istimewa, **supaya** saya merasa dihargai.

1. Nasabah Prestige dan Private melihat aksen kuning (Giallo) di beranda dan di badge tier.
2. Kurs valas spesial berlaku otomatis (S3.2 AC4).
3. Mengganti tier di admin mengubah tampilan pada pembukaan aplikasi berikutnya.

### S3.11 Relationship Manager
**Sebagai** nasabah Prestige, **saya ingin** tahu siapa RM saya dan menghubunginya dengan satu ketukan, **supaya** saya dapat bantuan pribadi.

1. Petugas membuat RM (nama, foto dari pustaka, nomor telepon, email) dan menugaskannya ke nasabah Prestige atau Private.
2. Beranda dan Profil nasabah menampilkan kartu RM dengan tombol Telepon dan Pesan, yang membuka aplikasi telepon atau SMS di HP tanpa izin tambahan.
3. Nasabah Everyday tidak melihat kartu RM.

---

## F23. Passkey

### S3.12 Masuk dengan passkey
**Sebagai** nasabah, **saya ingin** masuk dengan passkey, **supaya** saya tidak perlu mengetik password saat ganti HP.

1. Di Profil, nasabah bisa membuat passkey. Passkey disimpan oleh pengelola kata sandi di HP (misalnya Google Password Manager).
2. Layar masuk punya tombol "Sign in with a passkey" yang langsung menggantikan nomor HP dan password.
3. Server memverifikasi passkey dengan standar WebAuthn untuk domain orvyn.delvault.uk. Aplikasi terhubung ke domain lewat Digital Asset Links.
4. Passkey bisa dihapus dari Profil.
5. Masuk dengan passkey di HP baru tetap mengeluarkan HP lama, sama seperti password.

---

## KR7. Dokumentasi portofolio

### S3.13 README dan cerita proyek
**Sebagai** pemilik proyek, **saya ingin** repo yang siap dipamerkan, **supaya** orang yang membuka GitHub langsung paham dan terkesan.

1. README berisi ringkasan, screenshot utama (terang dan gelap), daftar fitur, arsitektur singkat, cara menjalankan, dan keputusan penting (ADR).
2. Cerita proyek menjelaskan masalah, pendekatan, keputusan desain, dan hasil (misalnya Vyna 30/30, buku besar yang tidak pernah selisih).
3. Tidak ada rahasia, data pribadi, atau nomor asli di repo.
