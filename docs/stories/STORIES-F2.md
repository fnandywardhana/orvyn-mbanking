# User Stories ORVYN: Fase 2 "bank yang membantu"

Sumber: `PRD-ORVYN.md` (F11–F17) dan sisa Fase 1 (push notification), istilah dari `CONTEXT.md`.
**Desain:** design system di `design-system/MASTER.md`. Layar dibuat dengan skill ui-ux-pro-max, grafik dengan skill dataviz.
**Perubahan:** AC bertanda "(diubah 0.9.0)" mengikuti `docs/features/2026-10-16-quality-pass/prd.md` bagian 8 dan 14 (PO-27).
**Definisi selesai:** sama dengan Fase 0 dan 1 (lihat `STORIES-F0-F1.md`), ditambah: setiap fitur bisa dimatikan dari Settings admin.

**Peran:** Nasabah, Petugas, dan Pemilik proyek (sama dengan Fase 1).

**Urutan pengerjaan:** push dulu, karena fitur Fase 2 (anggaran, minta uang, transfer terjadwal) memakai push. Lalu kantong, terjadwal, minta uang, insight, Vyna, e-statement, dan widget.

---

## Sisa Fase 1: push notification

### S2.0 Push notification sungguhan
**Sebagai** nasabah, **saya ingin** HP saya berbunyi saat ada uang masuk atau keluar, **supaya** saya tahu tanpa membuka aplikasi.

1. Setelah nasabah masuk, aplikasi mendaftarkan HP ke Firebase. Saat keluar atau saat masuk di HP lain, pendaftaran HP lama dihapus, jadi HP lama tidak menerima push lagi.
2. Setiap pesan baru di kotak masuk (transaksi, kartu, pesan petugas, hasil pendaftaran) juga dikirim sebagai push.
3. Di layar terkunci, push hanya menampilkan "ORVYN: new activity". Nominal dan nama baru terlihat setelah HP dibuka.
4. Ketukan pada push membuka ORVYN. Jika sesi sudah habis, nasabah memasukkan PIN dulu, lalu layar yang sesuai terbuka.
5. Jika izin notifikasi ditolak atau Firebase gagal, kotak masuk tetap berfungsi seperti biasa.
6. Satu-satunya data yang dikirim ke Firebase adalah token push. Halaman Privasi di aplikasi menyebutkan hal ini.

---

## F13. Kantong tujuan (Pocket)

### S2.1 Membuat kantong
**Sebagai** nasabah, **saya ingin** membuat kantong untuk satu tujuan, **supaya** uang tabungan liburan tidak tercampur dengan uang harian.

1. Nasabah mengisi nama (maksimal 24 huruf), memilih satu gambar dari 8 gambar bawaan, lalu boleh mengisi target nominal dan tanggal target.
2. Satu nasabah bisa punya maksimal 10 kantong.
3. Beranda menampilkan kantong di bawah rekening, lengkap dengan saldo dan progres terhadap target (garis dan persen).
4. Kantong tidak punya nomor rekening dan tidak bisa menerima transfer dari orang lain.
5. Kantong bisa diubah nama, gambar, dan targetnya. Kantong hanya bisa dihapus jika saldonya nol. Jika masih ada saldo, aplikasi menawarkan memindahkan semuanya ke tabungan.

### S2.2 Memindahkan uang ke dan dari kantong
**Sebagai** nasabah, **saya ingin** memindahkan uang ke kantong dan kembali ke tabungan, **supaya** saya bisa menabung kapan saja.

1. Perpindahan langsung terjadi tanpa PIN dan tanpa biaya, karena uang tetap milik nasabah sendiri.
2. Perpindahan tercatat sebagai Journal Entry, dan pengecekan buku besar tetap lolos.
3. Nominal tidak boleh lebih dari saldo asal. Pesan error menyebutkan saldo yang tersedia.
4. Saat target tercapai, aplikasi menampilkan ucapan selamat dan kotak masuk menerima pesan.
5. Riwayat kantong menampilkan setiap uang masuk dan keluar.

### S2.3 Tabung otomatis
**Sebagai** nasabah, **saya ingin** kantong terisi sendiri, **supaya** saya menabung tanpa perlu ingat.

1. Setiap kantong boleh memakai satu aturan: setiap gaji masuk (persen atau nominal tetap), setiap minggu pada hari tertentu (nominal tetap), atau pembulatan setiap pembayaran ke Rp10.000 terdekat.
2. "Gaji masuk" berarti uang masuk dengan kategori income dari petugas atau persona.
3. Jika saldo tabungan tidak cukup, tabungan otomatis dilewati kali itu, dan nasabah mendapat pesan di kotak masuk. Tidak ada saldo minus.
4. Setiap tabungan otomatis muncul di riwayat dengan label "Auto-save".
5. Aturan bisa dimatikan kapan saja.

---

## F14. Transfer terjadwal dan berulang

### S2.4 Menjadwalkan transfer
**Sebagai** nasabah, **saya ingin** menjadwalkan transfer sekali atau berulang, **supaya** uang kos dan kiriman ke orang tua tidak pernah terlambat.

1. Di layar periksa transfer ada pilihan "Send now" atau "Schedule": sekali di tanggal tertentu, setiap minggu, atau setiap bulan di tanggal tertentu.
2. PIN dimasukkan sekali saat membuat jadwal. Setiap pengiriman berikutnya tidak perlu PIN lagi.
3. Transfer dikirim pukul 07.00 WIB pada hari yang dijadwalkan. Untuk tanggal 29–31 di bulan yang lebih pendek, transfer dikirim di hari terakhir bulan itu.
4. Biaya, batas harian, dan aturan saldo sama dengan transfer biasa. Jika gagal, transfer tidak diulang di hari itu. Nasabah mendapat push dan pesan di kotak masuk berisi alasannya.
5. Saat bekerja, server mengirim transfer yang tertunda sekali saja, walaupun server sempat mati di jam kirim.

### S2.5 Mengelola jadwal
**Sebagai** nasabah, **saya ingin** melihat dan membatalkan jadwal transfer, **supaya** saya tetap memegang kendali.

1. Layar "Scheduled" menampilkan setiap jadwal: penerima, nominal, pola ulang, dan tanggal kirim berikutnya.
2. Nasabah bisa menghentikan sementara, melanjutkan, atau menghapus jadwal.
3. Hasil pengiriman terakhir (berhasil atau gagal beserta alasannya) terlihat di setiap jadwal.
4. Jadwal juga terlihat di halaman nasabah di admin.

---

## F15. Minta uang dan split bill

### S2.6 Meminta uang
**Sebagai** nasabah, **saya ingin** meminta uang ke sesama nasabah ORVYN, **supaya** teman yang berutang tinggal membayar.

1. Nasabah memasukkan nomor rekening ORVYN atau memilih penerima tersimpan, nominal, dan catatan singkat. Nominal minimal sama dengan minimum Transfer (sekarang Rp10,000). Server menolak permintaan di bawah minimum saat dibuat. Permintaan yang sudah terbuka tetap bisa dibayar dengan nominal aslinya walau minimum naik. (diubah 0.9.1)
2. Penerima mendapat push dan kartu permintaan di kotak masuk. Kartu itu membuka detail permintaan yang punya tombol "Pay" dan "Decline". (diubah 0.9.0)
3. "Pay" membuka layar periksa yang sudah terisi, baik dari detail permintaan maupun dari layar Requests. Pembayaran tetap memakai PIN. (diubah 0.9.0)
4. Permintaan kedaluwarsa setelah 7 hari. Peminta bisa membatalkannya selama belum dibayar.
5. Peminta mendapat push saat permintaan dibayar atau ditolak.
6. Satu nasabah bisa membuat maksimal 20 permintaan per hari, supaya tidak ada spam.

### S2.7 Split bill
**Sebagai** nasabah, **saya ingin** membagi tagihan makan ke beberapa teman, **supaya** saya tidak perlu menghitung dan menagih satu-satu.

1. Nasabah memasukkan total, lalu memilih 1–9 teman dari penerima tersimpan atau nomor rekening.
2. Pembagian default sama rata dan dibulatkan ke rupiah. Sisanya ditanggung pembuat. Nominal per orang bisa diubah. Total serta setiap bagian teman minimal sama dengan minimum Transfer (sekarang Rp10,000). Bagian di bawah minimum ditandai dan harus diubah sebelum dikirim. (diubah 0.9.1)
3. Setiap teman menerima permintaan terpisah (S2.6).
4. Layar split menampilkan siapa yang sudah bayar, belum, atau menolak.
5. Jika transaksi asal dipilih dari riwayat, totalnya terisi otomatis.

---

## F12. Insight dan anggaran

### S2.8 Kategori pengeluaran
**Sebagai** nasabah, **saya ingin** pengeluaran saya dikelompokkan sendiri, **supaya** saya tahu uang saya habis ke mana.

1. Setiap transaksi keluar masuk ke satu dari 7 kategori: makan, transportasi, belanja, tagihan, hiburan, transfer, dan lainnya.
2. Kategori diambil dari merchant QRIS, jenis tagihan, atau data persona. Transfer ke orang masuk kategori transfer.
3. Nasabah bisa mengganti kategori satu transaksi dari layar detail transaksi.
4. Kategori tampil sebagai ikon dan label di riwayat dan detail transaksi.

### S2.9 Layar insight
**Sebagai** nasabah, **saya ingin** melihat grafik pengeluaran per bulan dan per kategori, **supaya** saya bisa membandingkan dengan bulan lalu.

1. Grafik batang menampilkan total pengeluaran 6 bulan terakhir, dengan bulan ini disorot.
2. Daftar kategori bulan ini menampilkan nominal, porsi, dan perubahan dibanding bulan lalu (naik atau turun, dalam persen).
3. Ketukan pada kategori membuka transaksi bulan ini di kategori itu, dari tabungan dan kartu kredit, dengan penanda sumbernya. Jumlahnya sama dengan nominal kategori. (diubah 0.9.1)
4. Grafik punya label nilai yang bisa dibaca TalkBack, dan warnanya tidak menjadi satu-satunya penanda.
5. Jika belum ada transaksi bulan ini, layar menampilkan keadaan kosong yang jelas.

### S2.10 Anggaran
**Sebagai** nasabah, **saya ingin** memasang batas pengeluaran per kategori, **supaya** saya diingatkan sebelum kebablasan.

1. Nasabah memasang anggaran bulanan untuk kategori mana pun.
2. Saat pengeluaran mencapai 80% dan 100% anggaran, nasabah mendapat push dan pesan di kotak masuk. Setiap ambang hanya diingatkan sekali per bulan.
3. Layar insight menampilkan progres setiap anggaran.
4. Anggaran tidak memblokir pembayaran. Fungsinya hanya mengingatkan.

### S2.11 Kartu insight di beranda
**Sebagai** nasabah, **saya ingin** melihat satu kalimat insight di beranda, **supaya** saya tahu kebiasaan saya tanpa membuka grafik.

1. Beranda menampilkan satu kartu insight yang dihitung dari data, misalnya "Eating out is up 18% on last month."
2. Kalimat dipilih dari perubahan kategori terbesar, atau anggaran yang paling dekat batas.
3. Ketukan membuka layar insight. Kartu bisa ditutup dan muncul lagi minggu berikutnya.
4. Jika data belum cukup (kurang dari 2 bulan), kartu tidak muncul.

---

## F11. Vyna, asisten AI

### S2.12 Mengobrol dengan Vyna
**Sebagai** nasabah, **saya ingin** bertanya ke Vyna dengan bahasa sehari-hari, **supaya** saya tidak perlu mencari menu.

1. Vyna dibuka dari beranda. Jawabannya muncul kata per kata, dengan indikator saat Vyna sedang berpikir.
2. Vyna membalas dalam bahasa yang dipakai nasabah (Indonesia atau Inggris). Tampilan aplikasi tetap berbahasa Inggris.
3. Layar kosong menampilkan 3–4 contoh pertanyaan yang bisa diketuk.
4. Percakapan tersimpan selama sesi aplikasi dan hilang saat nasabah keluar. Riwayat obrolan tidak disimpan di server.
5. Jika server atau AI gagal, pesan error yang jelas muncul dengan tombol coba lagi.
6. Vyna menolak topik di luar perbankan dan ORVYN dengan sopan.

### S2.13 Vyna membaca data nasabah
**Sebagai** nasabah, **saya ingin** Vyna menjawab pertanyaan tentang uang saya, **supaya** jawabannya benar-benar berguna.

1. Vyna bisa menjawab: saldo, cari transaksi ("berapa total belanja di Kopi Senja bulan lalu?"), ringkasan pengeluaran per kategori atau periode, kantong, anggaran, jadwal transfer, kartu, dan FAQ.
2. Data diambil server lewat alat (tools) yang hanya bisa membaca data nasabah yang sedang masuk. Vyna tidak bisa membaca nasabah lain.
3. Angka di jawaban Vyna selalu berasal dari hasil alat, tidak dikarang.
4. Vyna menjawab benar minimal 27 dari 30 pertanyaan uji baku (KR6). Pertanyaan uji disimpan di repo dan bisa dijalankan ulang.

### S2.14 Vyna menyiapkan transfer dan pembayaran
**Sebagai** nasabah, **saya ingin** bilang "kirim 50 ribu ke Sinta" dan layar transfernya langsung siap, **supaya** lebih cepat.

1. Vyna bisa menyiapkan transfer ke penerima tersimpan atau nomor rekening, pembayaran tagihan, dan pengisian kantong.
2. Vyna tidak pernah mengirim uang sendiri. Vyna menampilkan kartu ringkasan dengan tombol "Review". Tombol itu membuka layar periksa yang sudah terisi, dan nasabah tetap memasukkan PIN.
3. Jika penerima tidak jelas (dua "Sinta"), Vyna bertanya dulu.
4. Kartu ringkasan menampilkan biaya dan pilihan BI-FAST atau RTGS sesuai aturan biasa.

### S2.15 Bicara dengan Vyna
**Sebagai** nasabah, **saya ingin** berbicara ke Vyna dan mendengar jawabannya, **supaya** saya bisa memakai Vyna tanpa mengetik.

1. Tombol mikrofon hanya muncul jika HP punya pengenalan suara yang berjalan di dalam HP (on-device). Rekaman tidak pernah keluar dari HP.
2. Izin mikrofon diminta saat tombol mikrofon pertama kali ditekan, bukan sebelumnya.
3. Nasabah bisa menyalakan "Read replies aloud". Jawaban dibacakan oleh mesin suara bawaan HP, dengan suara perempuan bila tersedia.
4. Teks yang dikenali tampil dulu di kolom input sebelum dikirim, sehingga bisa diperbaiki.

### S2.16 Petugas menjaga biaya Vyna
**Sebagai** pemilik proyek, **saya ingin** membatasi pemakaian Vyna, **supaya** biaya API tetap kecil.

1. Settings admin punya batas pesan Vyna per nasabah per hari (default 30) dan sakelar on/off.
2. Saat batas tercapai, Vyna memberi tahu nasabah bahwa batas hari ini sudah habis dan kapan bisa dipakai lagi.
3. Dashboard admin menampilkan jumlah pesan dan token Vyna hari ini dan 30 hari terakhir, beserta perkiraan biayanya dalam dolar.
4. API key tidak pernah dikirim ke aplikasi. Semua panggilan AI lewat server.

---

## F16. E-statement

### S2.17 E-statement bulanan
**Sebagai** nasabah, **saya ingin** mengunduh rekening koran bulanan dalam PDF, **supaya** saya punya bukti resmi untuk keperluan administrasi.

1. Nasabah memilih bulan (sampai 12 bulan ke belakang, tidak termasuk bulan berjalan), lalu PDF dibuat.
2. PDF berisi logo, nama, nomor rekening, periode, saldo awal, semua transaksi dengan saldo berjalan, saldo akhir, total masuk dan keluar, serta tulisan "Demo, not a real bank statement" di setiap halaman.
3. PDF dibuat di dalam HP dan dibuka lewat menu share Android, tanpa izin penyimpanan.
4. Saldo akhir di PDF sama dengan saldo di buku besar pada akhir bulan itu.

---

## F17. Widget

### S2.18 Widget layar utama
**Sebagai** nasabah, **saya ingin** widget ORVYN di layar utama HP, **supaya** saya bisa scan QRIS dengan satu ketukan.

1. Widget berisi tombol "Scan QRIS" dan "Show balance". Saldo tersembunyi secara default dan tertulis "Balance hidden". (diubah 0.9.0)
2. Ketukan "Balance" menampilkan saldo terakhir yang tersimpan selama 10 detik, lalu tersembunyi lagi. Jika belum ada data tersimpan, widget meminta nasabah membuka aplikasi.
3. "Scan QRIS" membuka ORVYN dan meminta PIN, lalu langsung ke kamera QRIS.
4. Setelah nasabah keluar, widget tidak menampilkan saldo lagi.
5. Widget mengikuti tema terang atau gelap HP.

---

## Admin untuk Fase 2

### S2.19 Petugas melihat fitur baru per nasabah
**Sebagai** petugas, **saya ingin** melihat kantong, jadwal transfer, dan permintaan uang setiap nasabah, **supaya** saya bisa membantu saat demo.

1. Halaman nasabah di admin menampilkan kantong (saldo dan target), jadwal transfer, dan permintaan yang masih terbuka.
2. Persona baru otomatis punya 1–2 kantong dan beberapa anggaran, supaya layar insight dan kantong langsung terisi saat demo.
3. Settings admin punya sakelar untuk kantong, jadwal transfer, minta uang, insight, Vyna, dan e-statement. Widget tidak punya sakelar, karena widget hanya menampilkan saldo yang tersimpan di HP dan tidak memanggil server. (diubah 0.9.0)
