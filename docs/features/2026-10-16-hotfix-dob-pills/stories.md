# User Stories ORVYN: Hotfix 0.8.1, tanggal lahir dan Quick Amount

Sumber: `prd.md` dan `requirement.md` di folder ini, istilah dari `CONTEXT.md`. Desain: `design.md` di folder ini (ditulis CX).
**Definisi selesai:** test server dan aplikasi yang ada tetap lolos, aplikasi ter-build sebagai 0.8.1 (versionCode 11), tidak ada izin Android baru, dan setiap kriteria di bawah lolos di Galaxy Note10+.

**Urutan pengerjaan:** H1 dan H2 (pendaftaran macet, paling mendesak), lalu H3, H4, H5.

---

## Tanggal lahir

### H1 Mengetik tanggal lahir dengan keyboard angka
**Sebagai** calon nasabah, **saya ingin** cukup mengetik angka tanggal lahir saya, **supaya** saya bisa menyelesaikan pendaftaran dengan keyboard angka bawaan HP.

1. Di langkah "About you", field "Date of birth" membuka keyboard angka bawaan HP (diuji dengan Samsung Keyboard di Note10+). Mengetik `17081990` tanpa tombol "/" menampilkan `17/08/1990`.
2. Garis miring muncul sendiri setelah angka ke-2 dan ke-4 tanpa diketik. Angka ke-9 dan seterusnya tidak masuk ke field.
3. Dari `17/08/1990`, setiap tekan tombol hapus menghilangkan satu angka, sampai field kosong, tanpa perlu menghapus garis miring tersendiri dan tanpa kursor tersangkut.
4. Kursor bisa diletakkan di tengah: dari `17/08/1990`, menghapus `08` lalu mengetik `09` menghasilkan `17/09/1990`.
5. Menempelkan `17/08/1990` atau `17081990` ke field sama-sama menampilkan `17/08/1990`.
6. Helper "DD/MM/YYYY" tetap tampil di bawah field selama tidak ada error.

### H2 Tanggal lahir tetap diperiksa dan tersimpan benar
**Sebagai** calon nasabah, **saya ingin** tahu bila tanggal lahir saya salah dan tidak perlu mengetik ulang bila sudah benar, **supaya** pendaftaran saya lancar dan datanya tepat.

1. Mengisi `31/02/1990` lalu menekan Continue menampilkan "Use the format DD/MM/YYYY." dan tidak lanjut. Hal yang sama terjadi bila angkanya kurang dari 8.
2. Mengisi tanggal yang membuat umur di bawah 17 tahun (misalnya tanggal hari ini 10 tahun lalu) lalu menekan Continue menampilkan "You need to be at least 17 to open an account." dan tidak lanjut.
3. Mengisi tanggal valid untuk umur 17 tahun ke atas, dengan field lain lengkap, membawa calon nasabah ke langkah berikutnya.
4. Kembali ke langkah "About you" setelah lanjut (tombol kembali, atau membuka lagi pendaftaran yang belum selesai) menampilkan tanggal yang tersimpan sebagai `DD/MM/YYYY`, misalnya `17/08/1990`, dan Continue bisa langsung ditekan tanpa mengetik ulang.
5. Setelah pendaftaran dikirim dengan `17/08/1990`, petugas melihat "Date of birth" `1990-08-17` di halaman Registration di admin (tidak bergeser satu hari).

---

## Quick Amount

### H3 Quick Amount deposito
**Sebagai** nasabah, **saya ingin** tombol nominal cepat di layar buka deposito sesuai minimumnya, **supaya** saya tidak memilih nominal yang lalu ditolak.

1. Layar "Open a deposit" menampilkan tepat empat Quick Amount: Rp1.000.000, Rp5.000.000, Rp10.000.000, dan Rp50.000.000, dengan urutan itu.
2. Tidak ada Quick Amount di bawah Rp1.000.000 di layar ini.
3. Mengetuk setiap Quick Amount mengisi nominal yang sama persis, dan tombol Next langsung aktif.
4. Label keempatnya sesuai `design.md` (label juta, bukan "1000k" atau "50000k").
5. Pada ukuran huruf terbesar di pengaturan HP, keempat Quick Amount terbaca utuh, tidak terpotong dan tidak keluar layar, di tema terang dan gelap.
6. Dengan TalkBack, setiap Quick Amount dibacakan sesuai `design.md`.

### H4 Quick Amount Fund dan Bond
**Sebagai** nasabah, **saya ingin** tombol nominal cepat saat membeli investasi sesuai aturan produknya, **supaya** setiap pilihan cepat bisa langsung dibeli.

1. Di layar beli setiap Fund, semua Quick Amount bernilai sama dengan atau di atas "Smallest purchase" produk itu (Rp100.000), dan nilainya sama dengan yang tertulis di `design.md`.
2. Di layar beli setiap Bond (ORI-D01 dan SR-D01), semua Quick Amount bernilai Rp1.000.000 atau lebih dan kelipatan Rp1.000.000, dan nilainya sama dengan yang tertulis di `design.md`.
3. Untuk produk yang sesuai profil risiko nasabah, mengetuk setiap Quick Amount di layar beli Fund dan Bond membuat tombol Buy aktif. Untuk produk di atas profil risiko, tombol Buy aktif setelah pernyataan risiko dicentang.
4. Layar beli dan jual Gold Savings tetap menampilkan 50k, 100k, 250k, 500k. Di layar jual Fund dan Gold Savings, Quick Amount yang lebih besar dari nilai Holding tetap mengisi nilai Holding, seperti di 0.8.0.
5. Label Quick Amount Rp1.000.000 ke atas sesuai `design.md` (bukan "1000k"), dan semuanya terbaca utuh pada ukuran huruf terbesar di tema terang dan gelap.

### H5 Layar nominal lain tetap berjalan
**Sebagai** nasabah, **saya ingin** tombol nominal cepat di layar lain tetap bekerja seperti biasa, **supaya** perbaikan ini tidak merusak transaksi saya sehari-hari.

1. Layar berikut tetap menampilkan 50k, 100k, 250k, 500k, dan mengetuk setiap Quick Amount mengisi nominal yang sama persis: Transfer, QRIS dengan nominal diketik, top up e-wallet, pindah uang kantong (masuk dan keluar), minta uang, split bill (total dan bagian teman), "Another amount" di bayar tagihan kartu, dan anggaran.
2. Di setiap layar pada AC1, tidak ada Quick Amount di bawah minimum layar itu: Transfer Rp10.000, QRIS Rp1.000, e-wallet Rp10.000, minta uang Rp1.000, bayar tagihan kartu Rp10.000, anggaran Rp10.000.
3. Bila Quick Amount lebih besar dari saldo, saldo kantong, atau sisa tagihan kartu, pesan yang sama seperti di 0.8.0 tetap tampil (misalnya "That's more than you owe (…)").
4. Satu transaksi Transfer dan satu QRIS memakai Quick Amount berhasil sampai bukti transaksi, dan pengecekan buku besar di admin tetap OK.
