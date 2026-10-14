# User Stories ORVYN: Fase 0 dan Fase 1

Sumber: `PRD-ORVYN.md` (F1–F10), istilah dari `CONTEXT.md`.
**Desain:** arah brand di [blueprint](https://claude.ai/artifact/BreTE3cUrXoVTRu7mVeStV) bagian 01. Layar final dibuat dengan skill ui-ux-pro-max di atas design system ORVYN sebelum tiap story dikodekan.

**Peran**
- **Nasabah:** penguji yang memakai aplikasi ORVYN (Customer).
- **Petugas:** orang yang masuk ke ORVYN Admin (Officer), dengan peran Super Admin, Operator, atau Konten.
- **Pemilik proyek:** pemilik ORVYN yang membagikan APK dan membuat akun penguji.

## Definisi selesai (berlaku untuk semua story)

Sebuah story baru dianggap selesai jika semua poin ini terpenuhi:
1. Semua teks berbahasa Inggris dan sudah melewati skill humanizer.
2. Layar rapi di tema terang dan gelap, dan tetap rapi saat ukuran font diperbesar sampai 200%.
3. Semua tombol punya label TalkBack, area sentuh minimal 48dp, dan kontras minimal WCAG AA.
4. Ada tampilan loading (skeleton), kosong, dan error yang dirancang.
5. Screenshot test sudah diperiksa, dan layar sudah dicek di HP Galaxy Note10+ yang terpasang di server.
6. Tidak ada izin Android baru di luar daftar PRD 7.4.
7. Semua perpindahan uang lewat Journal Entry, dan pengecekan buku besar tetap lolos.

---

## Fase 0: Fondasi

### S0.1 Server bisa dibuka dari internet
**Sebagai** pemilik proyek, **saya ingin** ORVYN Admin bisa dibuka dari internet dengan HTTPS, **supaya** tim saya bisa mencobanya dari mana saja.

1. Admin web terbuka di alamat HTTPS publik dengan sertifikat yang valid.
2. Server berjalan di Oracle Cloud Always Free dengan akun Pay As You Go, dan peringatan budget $1 aktif.
3. Server, database, dan HTTPS menyala kembali sendiri setelah VM di-restart.
4. Database dicadangkan otomatis setiap hari, dan 7 cadangan terakhir disimpan.
5. Alamat `/health` menjawab "ok" dalam kurang dari 300 ms.

### S0.2 Petugas masuk ke admin
**Sebagai** petugas, **saya ingin** masuk ke admin dengan email dan password, **supaya** hanya orang yang berhak yang bisa mengubah data demo.

1. Satu akun Super Admin dibuat saat server pertama kali dipasang. Password awalnya wajib diganti saat pertama masuk.
2. Password salah 5 kali berturut-turut mengunci akun petugas selama 15 menit.
3. Sesi petugas berakhir setelah 30 menit tidak aktif.
4. Menu yang tidak sesuai peran petugas tidak terlihat, dan aksesnya ditolak oleh server.

### S0.3 APK bisa dipasang dari kiriman manual
**Sebagai** pemilik proyek, **saya ingin** mengirim APK ORVYN lewat chat dan tim bisa memasangnya, **supaya** distribusi tidak butuh Play Store.

1. Aplikasi terdaftar di akun Android Developer Console Limited Distribution.
2. APK yang dikirim lewat chat bisa dipasang di HP satu penguji setelah ia menyetujui lewat link atau QR, tanpa Advanced Flow.
3. Aplikasi menampilkan ikon dan splash ORVYN, lalu layar selamat datang.
4. APK ditandatangani dengan satu kunci rilis yang sama untuk setiap versi, sehingga versi baru bisa dipasang menimpa versi lama.
5. Setiap APK dikirim bersama catatan rilis yang berisi nomor versi, isi perubahan, dan daftar izin.

### S0.4 Buku besar yang tidak pernah selisih
**Sebagai** pemilik proyek, **saya ingin** setiap saldo berasal dari buku besar double-entry, **supaya** demo tidak pernah menampilkan angka yang salah.

1. Journal Entry yang Posting-nya tidak berjumlah nol per mata uang selalu ditolak.
2. Saldo yang disimpan selalu sama dengan jumlah Posting rekening tersebut. Ini dicek setelah setiap Journal Entry dan setiap malam.
3. Dua transfer yang terjadi bersamaan dari rekening yang sama tidak bisa membuat saldo tabungan minus.
4. House Account yang dibutuhkan tersedia sejak awal: External Clearing, Fee Income, Merchant Settlement, Biller Settlement, Demo Equity.
5. Logika uang punya test otomatis yang dijalankan setiap kali kode dibangun.

### S0.5 Tampilan bisa diperiksa tanpa manusia
**Sebagai** pemilik proyek, **saya ingin** setiap layar bisa dirender jadi gambar dan dicek di HP asli, **supaya** kualitas UI terjaga walau tidak ada desainer manusia.

1. Setiap layar punya screenshot test untuk tema terang dan gelap.
2. Render layar beranda dibandingkan dengan screenshot dari Galaxy Note10+. Perbedaan font, warna, dan jarak dicatat.
3. Aplikasi bisa dipasang dan dibuka di HP lewat adb dengan satu perintah.
4. Screenshot dari HP hanya diambil saat ORVYN sedang tampil di layar.

### S0.6 Izin aplikasi dikunci
**Sebagai** pemilik proyek, **saya ingin** build gagal jika muncul izin di luar daftar yang disetujui, **supaya** aplikasi tidak pernah mengambil data dari HP penguji.

1. Daftar izin yang disetujui sama persis dengan PRD 7.4.
2. Build gagal dengan pesan yang menyebut nama izin yang tidak disetujui.
3. Aplikasi tidak memuat library analytics, crash reporter, atau iklan.
4. Pengenal perangkat adalah kode acak yang dibuat saat aplikasi pertama dipasang.

### S0.7 Design system ORVYN
**Sebagai** nasabah, **saya ingin** semua layar terlihat dan terasa sama, **supaya** ORVYN terasa seperti satu produk yang rapi.

1. Token warna (Nerazzurro, Nero, Giallo, netral, dan warna status) tersedia untuk tema terang dan gelap.
2. Font Plus Jakarta Sans dipakai dengan skala tipe yang tetap, dan angka saldo memakai digit tabular.
3. Komponen dasar tersedia: tombol, input, keypad nominal, kartu, item daftar, bottom sheet, dialog, snackbar, skeleton, empty state.
4. Admin web memakai token yang sama, sehingga warna dan font identik.
5. Ikon aplikasi dan logo tersedia dalam semua ukuran yang dibutuhkan Android.

---

## Fase 1: MVP

### F1. Daftar dan masuk

#### S1.1 Mengisi data pendaftaran
**Sebagai** calon nasabah, **saya ingin** mendaftar dari aplikasi, **supaya** saya bisa membuka rekening tanpa ke cabang.

1. Pendaftar mengisi nomor HP, lalu kode OTP 6 digit muncul sebagai notifikasi di HP yang sama.
2. Pendaftar mengisi nama, NIK, tanggal lahir, alamat, pekerjaan, rentang penghasilan, dan tujuan membuka rekening.
3. NIK wajib 16 digit angka. Umur minimal 17 tahun. Pesan error muncul di bawah kolom yang salah.
4. Nomor HP atau NIK yang sudah terdaftar ditolak dengan pesan yang jelas.
5. Data yang sudah diisi tidak hilang jika aplikasi ditutup di tengah jalan.

#### S1.2 Foto KTP dan selfie
**Sebagai** calon nasabah, **saya ingin** merasakan langkah foto KTP dan selfie, **supaya** pendaftaran terasa seperti bank sungguhan.

1. Kamera terbuka dengan bingkai panduan KTP, lalu bingkai wajah untuk selfie.
2. Izin kamera diminta tepat saat langkah ini, dengan penjelasan singkat sebelumnya.
3. Pendaftar bisa mengulang foto sebelum melanjutkan.
4. Foto tidak dikirim ke server dan dihapus dari aplikasi setelah langkah selesai.
5. Jika izin kamera ditolak, pendaftar tetap bisa melanjutkan dengan langkah ini dilewati.

#### S1.3 Membuat password dan PIN
**Sebagai** calon nasabah, **saya ingin** membuat password dan PIN, **supaya** rekening saya terlindungi.

1. Password minimal 8 karakter dan memuat huruf serta angka. Kekuatannya ditampilkan saat diketik.
2. PIN terdiri dari 6 digit, dimasukkan dua kali, dan tidak boleh berupa urutan (123456) atau angka yang sama semua (111111).
3. Setelah selesai, pendaftar melihat layar "Sedang diverifikasi" dengan penjelasan langkah berikutnya.
4. Password dan PIN disimpan dalam bentuk hash di server.

#### S1.4 Petugas memproses pendaftaran
**Sebagai** petugas Operator, **saya ingin** menyetujui atau menolak pendaftaran, **supaya** saya mengendalikan siapa yang menjadi nasabah.

1. Admin menampilkan daftar pendaftaran yang menunggu, dengan data yang diketik pendaftar.
2. Persetujuan membuat Customer, Savings Account dengan nomor rekening unik, dan Debit Card.
3. Petugas memilih tier saat menyetujui. Defaultnya Everyday.
4. Penolakan wajib menyertakan alasan, yang dikirim ke pendaftar.
5. Pendaftar menerima push notification dan pesan di kotak masuk untuk kedua hasil.

#### S1.5 Masuk pertama di HP
**Sebagai** nasabah, **saya ingin** masuk dengan nomor HP dan password di HP baru, **supaya** HP itu menjadi Registered Device saya.

1. Setelah berhasil masuk, HP ini menjadi Registered Device nasabah.
2. Jika sebelumnya ada HP lain yang terdaftar, HP itu otomatis keluar dan menerima pesan di kotak masuk.
3. Password salah 5 kali mengunci nasabah. Hanya petugas yang bisa membukanya.
4. Setelah masuk, nasabah ditawari untuk menyalakan biometrik.

#### S1.6 Masuk cepat dan keluar otomatis
**Sebagai** nasabah, **saya ingin** masuk dengan PIN atau sidik jari, **supaya** membuka ORVYN terasa cepat.

1. Di Registered Device, nasabah masuk dengan PIN 6 digit atau biometrik.
2. PIN salah 5 kali mengunci nasabah, dan sisa percobaan ditampilkan.
3. Aplikasi keluar otomatis setelah 5 menit tidak aktif, termasuk saat aplikasi berada di latar belakang.
4. Dari ketukan ikon sampai beranda tampil, waktunya kurang dari 2 detik di Galaxy Note10+.
5. Layar masuk menampilkan label kecil "Demo".

### F2. Beranda

#### S1.7 Melihat beranda
**Sebagai** nasabah, **saya ingin** melihat total saldo dan aksi utama begitu aplikasi terbuka, **supaya** saya langsung tahu kondisi keuangan saya.

1. Beranda menampilkan sapaan sesuai waktu, label tier, total saldo, empat aksi cepat, daftar rekening, dan 5 transaksi terakhir.
2. Satu ketukan menyembunyikan atau menampilkan semua nominal, dan pilihan itu diingat.
3. Tarik ke bawah untuk memperbarui data.
4. Saat offline, data terakhir tetap tampil dengan keterangan waktu pembaruan, dan aksi transaksi dinonaktifkan.
5. Saldo yang berubah tampil dengan animasi angka yang halus.

#### S1.8 Banner promo
**Sebagai** nasabah, **saya ingin** melihat promo terbaru di beranda, **supaya** saya tahu penawaran ORVYN.

1. Banner aktif tampil di beranda sesuai jadwal tayang.
2. Banner bisa digeser jika ada lebih dari satu.
3. Ketukan pada banner membuka halaman detail promo.
4. Jika tidak ada banner aktif, bagian ini tidak ditampilkan dan tidak menyisakan ruang kosong.

### F3. Rekening dan mutasi

#### S1.9 Detail rekening
**Sebagai** nasabah, **saya ingin** melihat detail setiap rekening, **supaya** saya bisa membagikan nomor rekening saya.

1. Detail menampilkan nama rekening, nomor, saldo, dan tanggal dibuka.
2. Nomor rekening bisa disalin dengan satu ketukan, dan muncul konfirmasi "Disalin".
3. Tombol bagikan mengirim teks berisi nama pemilik, nama bank, dan nomor rekening.

#### S1.10 Mutasi rekening
**Sebagai** nasabah, **saya ingin** melihat dan mencari riwayat transaksi, **supaya** saya tahu ke mana uang saya pergi.

1. Mutasi dikelompokkan per tanggal, dari yang terbaru.
2. Data dimuat bertahap saat digulir, tanpa batas.
3. Filter tersedia untuk rentang tanggal, uang masuk/keluar, dan jenis transaksi.
4. Pencarian bisa memakai nama lawan transaksi, catatan, atau nominal.
5. Riwayat 6 bulan dari template persona tampil lengkap dan konsisten dengan saldo.

#### S1.11 Detail dan bukti transaksi
**Sebagai** nasabah, **saya ingin** melihat detail transaksi dan membagikan buktinya, **supaya** saya bisa menunjukkan bahwa saya sudah membayar.

1. Detail menampilkan jenis, nominal, biaya, lawan transaksi, catatan, waktu (WIB), nomor referensi, dan status.
2. Transaksi yang dibatalkan petugas ditandai "Dibatalkan" dan terhubung ke transaksi pembatalannya.
3. Tombol bagikan membuat gambar bukti transaksi bergaya ORVYN, lalu membuka menu bagikan Android.
4. Gambar bukti tidak disimpan ke galeri, kecuali nasabah memilihnya sendiri dari menu bagikan.

### F4. Transfer

#### S1.12 Transfer sesama ORVYN
**Sebagai** nasabah, **saya ingin** mengirim uang ke nasabah ORVYN lain, **supaya** saya bisa membayar teman dengan instan.

1. Setelah nomor rekening diketik, nama penerima muncul otomatis. Nomor yang tidak ditemukan atau milik sendiri ditolak.
2. Alurnya: penerima → nominal → catatan (opsional) → periksa → PIN → berhasil.
3. Layar periksa menampilkan penerima, nominal, biaya Rp0, dan saldo setelah transfer.
4. Saldo pengirim berkurang dan saldo penerima bertambah dalam satu Journal Entry. Keduanya menerima push notification.
5. Transfer ditolak dengan pesan jelas jika saldo kurang atau melewati Daily Limit, dengan menyebut sisa limit hari ini.
6. Ketukan ganda pada tombol konfirmasi tidak pernah membuat transfer terkirim dua kali.

#### S1.13 Transfer ke bank lain
**Sebagai** nasabah, **saya ingin** mengirim uang ke bank lain lewat BI-FAST atau RTGS, **supaya** saya bisa membayar orang yang tidak memakai ORVYN.

1. Nasabah memilih bank dari daftar yang bisa dicari, lalu mengetik nomor rekening.
2. Nomor dengan panjang yang valid selalu menampilkan nama dari External Directory, dan nama itu konsisten untuk nomor yang sama.
3. BI-FAST: biaya Rp2.500, maksimal Rp250 juta per transaksi. RTGS: hanya untuk nominal di atas Rp100 juta, biaya Rp25.000.
4. Saldo pengirim berkurang sebesar nominal ditambah biaya. Lawan posting-nya masuk ke External Clearing dan Fee Income.
5. Jika nominal di atas Rp100 juta, layar periksa menawarkan pilihan BI-FAST atau RTGS, lengkap dengan biaya masing-masing.

#### S1.14 Penerima favorit
**Sebagai** nasabah, **saya ingin** menyimpan penerima yang sering saya kirimi uang, **supaya** transfer berikutnya lebih cepat.

1. Setelah transfer berhasil, nasabah bisa menyimpan penerima dengan satu ketukan.
2. Layar pilih penerima menampilkan favorit, lalu penerima terakhir, lalu tombol penerima baru.
3. Favorit bisa diberi nama panggilan, diubah, dan dihapus.
4. Pencarian favorit bisa memakai nama atau nomor rekening.

### F5. QRIS

#### S1.15 Bayar dengan QRIS
**Sebagai** nasabah, **saya ingin** membayar di merchant dengan scan QR, **supaya** saya tidak perlu uang tunai.

1. Tombol QRIS membuka kamera, dan scanner langsung aktif.
2. Gambar QR bisa dipilih dari galeri lewat Photo Picker, tanpa izin penyimpanan.
3. QR statis meminta nominal. QR dinamis menampilkan nominal yang tidak bisa diubah.
4. QR yang bukan QRIS, atau merchant yang tidak terdaftar, menampilkan pesan yang jelas.
5. Pembayaran memotong saldo nasabah, dengan lawan posting di Merchant Settlement, dan dihitung dalam Daily Limit.

#### S1.16 QR untuk menerima uang
**Sebagai** nasabah, **saya ingin** menampilkan QR saya sendiri, **supaya** teman sesama ORVYN bisa membayar saya dengan scan.

1. "QR-ku" menampilkan QR berisi nomor rekening nasabah, dengan nominal opsional.
2. Saat QR-ku di-scan oleh nasabah ORVYN lain, alurnya menjadi Internal Transfer dengan penerima yang sudah terisi.
3. Kecerahan layar dinaikkan selama QR tampil, lalu dikembalikan seperti semula.

#### S1.17 Petugas mengelola merchant
**Sebagai** petugas Operator, **saya ingin** membuat merchant dummy dan QR-nya, **supaya** penguji punya sesuatu untuk di-scan.

1. Petugas bisa menambah, mengubah, dan menonaktifkan merchant (nama, kota, kategori).
2. Admin membuat QR statis dan QR dinamis (dengan nominal) dalam format QRIS.
3. QR bisa diunduh sebagai gambar, atau ditampilkan layar penuh untuk di-scan dari monitor.
4. Server berisi 10 merchant awal: kafe, minimarket, restoran, parkir, dan lain-lain.

### F6. Bayar dan beli

#### S1.18 Beli token PLN
**Sebagai** nasabah, **saya ingin** membeli token listrik, **supaya** listrik di rumah tidak padam.

1. Nasabah mengetik nomor meter, lalu nama pelanggan dummy dan daya listrik muncul.
2. Pilihan nominal: Rp20rb, 50rb, 100rb, 200rb, 500rb, dan 1jt, dengan biaya admin Rp2.500.
3. Setelah berhasil, token 20 digit tampil dalam format 4-4-4-4-4 dan bisa disalin.
4. Nomor meter tersimpan untuk pembelian berikutnya.

#### S1.19 Bayar tagihan PLN dan BPJS
**Sebagai** nasabah, **saya ingin** membayar tagihan bulanan, **supaya** saya tidak terkena denda.

1. Nasabah mengetik nomor pelanggan, lalu nama, periode, dan jumlah tagihan muncul.
2. Tagihan yang sudah lunas menampilkan pesan "Tagihan bulan ini sudah dibayar".
3. Setelah dibayar, tagihan itu berstatus lunas untuk periode tersebut.
4. Nomor pelanggan tersimpan untuk pembayaran berikutnya.

#### S1.20 Beli pulsa dan paket data
**Sebagai** nasabah, **saya ingin** membeli pulsa untuk nomor siapa pun, **supaya** saya bisa tetap terhubung.

1. Operator terdeteksi otomatis dari awalan nomor, dan logonya diganti dengan nama operator.
2. Nomor HP nasabah sendiri sudah terisi sebagai pilihan pertama.
3. Pilihan pulsa dan paket data ditampilkan sebagai kartu dengan harga.
4. Nomor yang tidak dikenali menampilkan pesan yang jelas.

#### S1.21 Top up e-wallet
**Sebagai** nasabah, **saya ingin** mengisi saldo e-wallet, **supaya** saya bisa memakainya di aplikasi lain.

1. Pilihan e-wallet: GoPay, OVO, DANA, ShopeePay, ditampilkan sebagai nama tanpa logo merek.
2. Nomor e-wallet menampilkan nama pemilik dummy sebelum nasabah membayar.
3. Nominal bebas mulai Rp10.000, dengan biaya admin Rp1.000.

#### S1.22 Petugas mengelola biller
**Sebagai** petugas Operator, **saya ingin** mengatur biller dan data pelanggannya, **supaya** skenario pembayaran bisa diatur.

1. Petugas bisa mengatur daftar biller, biaya admin, dan nominal yang tersedia.
2. Petugas bisa menambah nomor pelanggan dummy dengan nama dan tagihan per periode.
3. Nomor pelanggan yang tidak terdaftar tetap menghasilkan nama dan tagihan rekaan yang konsisten.

### F7. Kartu

#### S1.23 Melihat kartu
**Sebagai** nasabah, **saya ingin** melihat semua kartu saya, **supaya** saya tahu kartu apa saja yang saya punya.

1. Kartu tampil sebagai visual kartu ORVYN yang bisa digeser: debit, ORVYN Cashback, ORVYN Voyage.
2. Nomor kartu ditampilkan tersamar (•••• 4021).
3. Tombol "Lihat detail" meminta PIN, lalu menampilkan nomor lengkap, CVV, dan masa berlaku.
4. Detail tersembunyi lagi setelah 30 detik atau saat aplikasi ke latar belakang. Layar ini tidak bisa di-screenshot.

#### S1.24 Mengendalikan kartu
**Sebagai** nasabah, **saya ingin** memblokir sementara dan mengatur penggunaan kartu, **supaya** kartu saya aman jika hilang.

1. Blokir sementara bisa dinyalakan dan dimatikan dengan satu sakelar, dan visual kartu berubah jadi abu-abu.
2. Transaksi online, luar negeri, dan contactless bisa dinyalakan atau dimatikan satu per satu.
3. Limit harian kartu bisa diatur dalam rentang yang ditentukan admin.
4. Simulasi transaksi kartu dari admin ditolak jika kartu diblokir atau penggunaannya dimatikan, dan nasabah menerima notifikasi "Transaksi ditolak".

#### S1.25 Tagihan kartu kredit
**Sebagai** nasabah kartu kredit, **saya ingin** melihat dan membayar tagihan, **supaya** saya tidak telat bayar.

1. Kartu kredit menampilkan limit, sisa limit, tagihan berjalan, pembayaran minimum, dan tanggal jatuh tempo.
2. Pilihan bayar: penuh, minimum, atau nominal lain, dari Savings Account.
3. Pembayaran menambah sisa limit dengan segera.
4. Daftar transaksi kartu tampil terpisah dari mutasi tabungan.

#### S1.26 Petugas mensimulasikan transaksi kartu
**Sebagai** petugas Operator, **saya ingin** membuat transaksi kartu dummy, **supaya** kartu penguji punya riwayat dan bisa diuji.

1. Petugas memilih kartu, merchant, nominal, dan jenisnya (online, luar negeri, atau contactless).
2. Transaksi mengikuti status blokir, sakelar penggunaan, limit harian, dan sisa limit kartu.
3. Nasabah menerima push notification untuk transaksi berhasil maupun ditolak.

### F8. Notifikasi

#### S1.27 Notifikasi dan kotak masuk
**Sebagai** nasabah, **saya ingin** diberi tahu setiap ada uang masuk atau keluar, **supaya** saya langsung tahu jika ada yang tidak beres.

1. Push notification muncul untuk setiap transaksi, hasil pendaftaran, dan pesan dari petugas.
2. Izin notifikasi diminta setelah nasabah pertama kali masuk, bukan saat aplikasi pertama dibuka.
3. Semua notifikasi tersimpan di kotak masuk, dengan penanda belum dibaca dan jumlahnya di ikon.
4. Ketukan pada notifikasi membuka layar yang sesuai, misalnya detail transaksi.
5. Jika izin notifikasi ditolak, kotak masuk tetap berfungsi.

#### S1.28 Petugas mengirim push dan pengumuman
**Sebagai** petugas Konten, **saya ingin** mengirim pesan ke nasabah, **supaya** saya bisa mengumumkan promo atau info demo.

1. Target pesan bisa semua nasabah, satu tier, atau satu nasabah.
2. Pesan berisi judul dan isi, dengan batas panjang yang sesuai notifikasi Android.
3. Pengumuman pop-up tampil sekali saat aplikasi dibuka, sampai tanggal berakhirnya.
4. Admin menampilkan pratinjau pesan sebelum dikirim.

### F9. Profil dan pengaturan

#### S1.29 Tema
**Sebagai** nasabah, **saya ingin** memilih tema, **supaya** aplikasi nyaman dipakai.

1. Tema bisa diatur: ikut sistem, terang, atau gelap, dan langsung berlaku tanpa restart.
2. Angka dan tanggal memakai format Inggris (Rp1,000,000 dan "14 Oct 2026, 19:27 WIB").

#### S1.30 Keamanan akun
**Sebagai** nasabah, **saya ingin** mengganti PIN dan password, **supaya** saya tetap memegang kendali atas akun saya.

1. Mengganti PIN membutuhkan PIN lama. Mengganti password membutuhkan password lama.
2. Biometrik bisa dinyalakan dan dimatikan.
3. Tombol keluar meminta konfirmasi, lalu kembali ke layar masuk.

#### S1.31 Bantuan dan privasi
**Sebagai** nasabah, **saya ingin** mencari jawaban sendiri dan tahu data apa yang dipakai aplikasi, **supaya** saya merasa aman.

1. FAQ dari admin tampil per kategori dan bisa dicari.
2. Halaman Privasi menjelaskan isi PRD 7.4 dengan bahasa sederhana.
3. Halaman Tentang menampilkan versi aplikasi dan keterangan bahwa ORVYN Bank adalah bank fiktif.

### F10. ORVYN Admin

#### S1.32 Membuat nasabah dari template persona
**Sebagai** pemilik proyek, **saya ingin** membuat akun penguji dari template persona, **supaya** setiap penguji langsung punya data yang hidup.

1. Petugas memilih template (Raka, Sinta, atau Hendra), lalu mengisi nama, nomor HP, dan password awal.
2. Nasabah yang terbentuk punya tier, rekening, kartu, dan riwayat transaksi 6 bulan yang sesuai template.
3. Riwayat transaksi dibuat lewat Journal Entry, sehingga saldo akhir dan mutasi konsisten.
4. Admin menampilkan ringkasan untuk dikirim ke penguji: nomor HP, password awal, dan cara memasang APK.
5. Petugas bisa membuat nasabah dummy massal sebagai penerima transfer.

#### S1.33 Mengelola nasabah
**Sebagai** petugas Operator, **saya ingin** mengubah dan mengendalikan nasabah, **supaya** saya bisa membantu penguji yang bermasalah.

1. Daftar nasabah bisa dicari dengan nama, nomor HP, atau nomor rekening.
2. Petugas bisa mengubah data diri dan tier, mengunci atau membuka nasabah, serta mereset PIN dan password.
3. Reset PIN atau password mengeluarkan nasabah dari HP-nya.
4. "Lihat sebagai nasabah" menampilkan beranda, rekening, dan mutasi nasabah tanpa bisa mengubah apa pun.

#### S1.34 Penyesuaian saldo dan uang masuk
**Sebagai** petugas Operator, **saya ingin** menambah atau mengurangi saldo nasabah, **supaya** saya bisa mengatur skenario demo.

1. Penyesuaian saldo wajib menyertakan alasan, dan tampil di mutasi nasabah sebagai "Penyesuaian saldo".
2. Uang masuk tampil sebagai transaksi realistis, misalnya "Gaji PT Maju Jaya" atau "Transfer dari BCA a.n. Dimas".
3. Pengurangan yang akan membuat saldo minus ditolak.
4. Nasabah menerima push notification untuk setiap uang masuk.

#### S1.35 Daftar transaksi dan pembatalan
**Sebagai** petugas Operator, **saya ingin** melihat semua transaksi dan membatalkan yang salah, **supaya** demo tetap rapi.

1. Daftar transaksi bisa dicari dan difilter berdasarkan nasabah, jenis, tanggal, dan nominal.
2. Pembatalan membuat Journal Entry baru yang membalik transaksi asli. Transaksi asli tidak diubah.
3. Pembatalan ditolak jika akan membuat saldo tabungan siapa pun menjadi minus. Pesannya menyebut rekening dan kekurangannya.
4. Satu transaksi hanya bisa dibatalkan sekali.
5. Dashboard menampilkan ringkasan hari ini: jumlah nasabah, transaksi, dan total nominal, serta status pengecekan buku besar.

#### S1.36 Konten aplikasi
**Sebagai** petugas Konten, **saya ingin** mengatur banner, FAQ, dan artikel, **supaya** isi aplikasi bisa diganti tanpa update APK.

1. Banner punya gambar, judul dan teks, halaman detail, jadwal tayang, dan urutan.
2. FAQ dan artikel punya kategori dan teks.
3. Perubahan langsung terlihat di aplikasi saat beranda diperbarui.
4. Gambar yang diunggah diperkecil otomatis supaya cepat dimuat.

#### S1.37 Pengaturan demo
**Sebagai** petugas Super Admin, **saya ingin** mengatur aturan bank, **supaya** skenario demo bisa diubah tanpa mengubah kode.

1. Daily Limit per tier dan biaya transfer bisa diubah.
2. External Directory bisa diisi nomor rekening dan nama tertentu.
3. Fitur bisa dinyalakan atau dimatikan per fitur, dan aplikasi menyembunyikan fitur yang mati.
4. Mode maintenance menampilkan layar pemberitahuan di aplikasi dan menolak transaksi.

#### S1.38 Petugas, audit log, dan reset demo
**Sebagai** petugas Super Admin, **saya ingin** mengatur petugas lain dan melacak perubahan, **supaya** demo aman dikelola beberapa orang.

1. Super Admin bisa menambah, menonaktifkan, dan mengubah peran petugas.
2. Setiap perubahan oleh petugas tercatat sebagai Audit Entry: siapa, apa, kapan, nilai sebelum, dan nilai sesudah.
3. Audit log bisa difilter per petugas, jenis perubahan, dan tanggal, dan tidak bisa diubah atau dihapus.
4. Reset demo mengembalikan semua data ke kondisi awal, setelah petugas mengetik "RESET" dan memasukkan passwordnya.
5. Reset demo tidak menghapus akun petugas maupun audit log.
