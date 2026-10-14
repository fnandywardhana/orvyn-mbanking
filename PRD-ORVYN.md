# PRD: ORVYN

**Produk:** ORVYN (aplikasi mobile banking Android) + ORVYN Admin (website pengelola)
**Bank:** ORVYN Bank (fiktif)
**Tagline:** *Your money, one step ahead.*
**Status:** Draft v1 · 14 Okt 2026 · menunggu review
**Blueprint brand & riset:** https://claude.ai/artifact/BreTE3cUrXoVTRu7mVeStV

---

## 1. Ringkasan

ORVYN adalah aplikasi mobile banking demo untuk portofolio. Tampilan dan pengalamannya dibuat setara aplikasi bank terbaik dunia, dengan nasabah yang meniru profil nasabah OCBC Indonesia. Semua uang dan data adalah simulasi, dan semuanya bisa diatur dari website admin. Dokumen ini menjelaskan apa yang dibuat, untuk siapa, dan dalam urutan apa.

## 2. Kontak

| Nama | Peran | Catatan |
|---|---|---|
| Pemilik proyek | Product owner | Menentukan arah, mereview setiap fase, menyediakan akun (GitHub, Google/Firebase, Anthropic, Oracle Cloud) |
| Claude | PM, desainer, engineer | Mengerjakan seluruh riset, desain, kode, deploy, dan dokumentasi. Tidak ada developer manusia. |
| Tim pemilik proyek | Penguji (maks. 10 orang, semua Android) | Masing-masing mendapat 1 akun nasabah dari pemilik proyek, mencoba APK, dan memberi masukan di akhir tiap fase |

## 3. Latar belakang

**Konteksnya.** Proyek ini adalah karya portofolio pribadi: sebuah bank fiktif yang terasa seperti bank sungguhan. Penguji harus bisa daftar, menerima uang, transfer ke teman, bayar QRIS, dan bertanya ke asisten AI, lalu melihat saldo berubah dengan benar di kedua sisi.

**Kenapa sekarang.**
- Nasabah bank di Indonesia sudah hampir sepenuhnya digital. Di OCBC, hanya sekitar 2% transaksi terjadi di kantor cabang. Bagi nasabah, aplikasi adalah banknya.
- Investasi juga pindah ke aplikasi. Porsi transaksi wealth OCBC lewat kanal digital naik dari 30% (2024) ke 44% (2025).
- Asisten AI yang bisa memahami bahasa sehari-hari sekarang cukup murah dan cepat untuk dipasang di aplikasi bank. Satu tahun lalu, ini masih mahal dan lambat.
- Android terbaru membawa passkey, widget modern, dan animasi yang lebih halus. Bank besar biasanya lambat mengadopsinya, sehingga demo ini bisa terlihat lebih maju.

## 4. Tujuan

**Tujuan utama:** membuat demo bank yang meyakinkan. Orang yang mencobanya harus merasa sedang memakai bank sungguhan yang dibuat dengan sangat baik.

**Kenapa penting:** ini karya portofolio. Kualitas yang dinilai orang adalah rasa dan detail: alur yang jelas, tampilan rapi, angka yang selalu benar.

**Key results**

| # | Key result | Kapan diukur |
|---|---|---|
| KR1 | 5 dari 5 penguji menyelesaikan transfer sesama ORVYN pertama mereka tanpa bantuan, dalam waktu kurang dari 60 detik | Akhir Fase 1 |
| KR2 | Nol selisih saldo: total debit selalu sama dengan total kredit, dicek otomatis setiap transaksi dan setiap hari | Terus-menerus |
| KR3 | Nol crash yang dilaporkan penguji selama uji tim (tidak ada crash reporter otomatis, lihat 7.4) | Setiap fase |
| KR4 | Aplikasi terbuka sampai beranda dalam kurang dari 2 detik di HP Android kelas menengah | Setiap fase |
| KR5 | Rata-rata nilai penguji minimal 4,5 dari 5 untuk tampilan dan kemudahan (survei 5 pertanyaan) | Setiap fase |
| KR6 | Vyna menjawab benar minimal 27 dari 30 pertanyaan uji baku (ID dan EN) | Akhir Fase 2 |
| KR7 | Repo GitHub punya README, screenshot, dan cerita proyek yang siap dipamerkan | Akhir Fase 3 |

## 5. Segmen pasar

Segmen dibagi berdasarkan **kebutuhan**, mengikuti struktur layanan OCBC Indonesia (Nyala, Premier Banking, Private Bank).

| Segmen ORVYN | Padanan OCBC | Pekerjaan yang ingin diselesaikan nasabah |
|---|---|---|
| **Everyday** | Nyala | "Bantu saya bayar, transfer, dan menabung dengan cepat, dan beri tahu kalau saya boros." |
| **Prestige** | Premier Banking | "Tunjukkan seluruh kekayaan saya di satu tempat, dan hubungkan saya dengan orang yang bisa membantu." |
| **Private** | Private Bank | "Layani saya secara eksklusif." (Di demo ini hanya berupa profil dan tampilan.) |

**Persona utama** (rincian ada di blueprint):
- **Raka, 28, Everyday, Jakarta.** Karyawan, gaji Rp12 juta. Rajin menabung, tapi tidak pernah mencatat pengeluaran dan belum punya dana darurat. Ia mewakili 80% responden OCBC Financial Fitness Index 2026 yang tidak mencatat keuangan, dan 73% yang belum punya dana darurat.
- **Sinta, 34, Everyday, Surabaya.** Pemilik usaha kuliner. Sering transfer ke pemasok, uang usaha dan pribadi tercampur.
- **Pak Hendra, 47, Prestige, Jakarta.** AUM Rp3 miliar, sering bepergian ke luar negeri, anaknya kuliah di Australia.

**Batasan:**
- Hanya Android. Penguji maksimal 10 orang, semuanya memakai Android dengan layanan Google.
- Bukan bank sungguhan: tidak ada uang asli dan tidak ada koneksi ke jaringan pembayaran asli.
- Biaya infrastruktur harus nol. Satu-satunya biaya adalah API Claude untuk Vyna.

## 6. Proposisi nilai

| Kebutuhan | Yang didapat nasabah | Rasa sakit yang hilang | Lebih baik dari pesaing karena |
|---|---|---|---|
| Transaksi harian cepat | Transfer dan QRIS dalam 3 ketukan + PIN | Menu bertumpuk dan istilah bank yang membingungkan | Aksi utama selalu di jangkauan jempol, bahasanya manusiawi |
| Tahu ke mana uang pergi | Pengeluaran otomatis dikelompokkan, ada grafik dan peringatan anggaran | Harus mencatat sendiri | Insight muncul sendiri di beranda tanpa perlu dicari |
| Menabung untuk tujuan | Kantong dengan target dan tabung otomatis | Uang tabungan tercampur dengan uang jajan | Kantong dibuat dalam hitungan detik, bisa diisi otomatis tiap gajian |
| Bertanya tanpa menunggu CS | Vyna menjawab dan menyiapkan transaksi lewat percakapan | Antre call center | Vyna tahu data nasabah sendiri, bukan cuma FAQ |
| Melihat seluruh aset | Satu angka total aset untuk tabungan, deposito, valas, reksadana, emas | Membuka banyak aplikasi | Dashboard wealth bergaya DBS dengan kemudahan Revolut |

**Kurva nilai.** Dibanding mobile banking bank besar Indonesia, ORVYN sengaja **lebih tinggi** di kejelasan tampilan, kecepatan alur, insight otomatis, dan asisten AI. ORVYN sengaja **lebih rendah** di jumlah menu dan produk. Lebih sedikit, tapi setiap fitur dibuat sangat baik.

## 7. Solusi

### 7.1 UX dan alur utama

**Prinsip desain**
1. Saldo adalah elemen terbesar di beranda, dan bisa disembunyikan dengan satu ketukan.
2. Empat aksi utama (Transfer, QRIS, Bayar, Top up) selalu terlihat. QRIS ada di tengah navigasi bawah.
3. Setiap transaksi melewati alur yang sama: **pilih → isi → periksa → PIN → berhasil**. Layar "periksa" selalu menampilkan biaya dan saldo setelah transaksi.
4. Layar berhasil punya animasi singkat, getaran halus, dan tombol bagikan bukti.
5. Semua teks tersedia dalam ID dan EN, dan semuanya sudah dilewatkan skill humanizer.
6. Dark mode penuh, mengikuti setelan HP.
7. Aksesibilitas: label TalkBack di semua tombol, area sentuh minimal 48dp, tetap rapi saat ukuran font diperbesar sampai 200%, kontras warna minimal WCAG AA.
8. Setiap layar punya tampilan loading (skeleton), kosong, dan error yang dirancang. Tidak ada layar putih kosong.

**Alur utama**
- **Daftar:** Selamat datang → nomor HP + OTP (simulasi) → data diri → foto KTP → selfie → buat password → buat PIN → status "sedang diverifikasi" → admin menyetujui → notifikasi "Rekeningmu aktif" → beranda.
- **Transfer:** Transfer → pilih penerima (favorit / terakhir / baru) → nama penerima muncul otomatis → nominal → catatan → periksa → PIN → berhasil.
- **QRIS:** QRIS → kamera terbuka → scan → nama merchant → nominal (jika QR statis) → periksa → PIN → berhasil.

Wireframe dan desain final dibuat dengan skill ui-ux-pro-max sebelum tiap fase dikodekan.

### 7.2 Fitur utama

#### Fase 1: MVP, "bank yang bisa dipakai"

**F1. Daftar, masuk, keamanan dasar**
- Pendaftaran mengisi: nama, NIK (16 digit, divalidasi), tanggal lahir, alamat, pekerjaan, rentang penghasilan, dan tujuan membuka rekening.
- Foto KTP dan selfie diambil dengan kamera hanya untuk pengalaman alurnya. Foto **tidak dikirim ke server** dan langsung dihapus dari aplikasi setelah langkah itu selesai. Admin menyetujui berdasarkan data yang diketik. Tidak ada verifikasi sungguhan.
- OTP disimulasikan: kode muncul sebagai notifikasi di HP.
- Masuk pertama di sebuah HP memakai nomor HP + password. Setelah itu cukup PIN 6 digit atau biometrik.
- PIN salah 5 kali membuat akun terkunci, dan hanya bisa dibuka oleh admin.
- Keluar otomatis setelah 5 menit tidak aktif.

**F2. Beranda**
- Menampilkan total saldo, kartu rekening, aksi cepat, banner promo dari admin, dan 5 transaksi terakhir.
- Tarik ke bawah untuk menyegarkan.
- Saldo terakhir tetap tampil saat offline, dengan keterangan waktu pembaruan.

**F3. Rekening dan mutasi**
- Daftar rekening dan detail tiap rekening (nomor rekening bisa disalin dan dibagikan).
- Mutasi bisa digulir tanpa batas, difilter (tanggal, masuk/keluar, jenis), dan dicari.
- Detail transaksi bisa dibagikan sebagai gambar bukti transaksi.

**F4. Transfer**
- **Sesama ORVYN:** instan, gratis. Saldo pengirim berkurang dan saldo penerima bertambah dalam satu transaksi database. Penerima langsung mendapat notifikasi.
- **BI-FAST** (simulasi): biaya Rp2.500, maksimal Rp250 juta per transaksi.
- **RTGS** (simulasi): untuk nominal di atas Rp100 juta, biaya Rp25.000.
- Transfer ke bank lain hanya mengurangi saldo pengirim. Nama penerima di bank lain diambil dari daftar rekening dummy yang diatur admin.
- Penerima bisa disimpan sebagai favorit.
- Batas transfer harian mengikuti tier: Everyday Rp100 juta, Prestige Rp1 miliar. Angkanya bisa diubah admin.
- Transaksi ditolak dengan pesan jelas jika saldo kurang, melewati batas, atau rekening tujuan tidak ditemukan.

**F5. QRIS**
- Scan QR dari kamera, atau pilih gambar dari galeri.
- QR merchant dummy dibuat di admin dengan format standar QRIS (EMVCo), dalam versi statis dan dinamis.
- "QR-ku": nasabah bisa menampilkan QR untuk menerima uang dari sesama ORVYN.

**F6. Bayar dan beli**
- **Token PLN:** nomor meter → nama pelanggan dummy → pilih nominal → token 20 digit ditampilkan.
- **Tagihan PLN** pascabayar.
- **Pulsa dan paket data:** operator terdeteksi otomatis dari awalan nomor.
- **Top up e-wallet:** GoPay, OVO, DANA, ShopeePay. Hanya nama, tanpa logo merek.
- **BPJS Kesehatan.**
- Daftar biller dan nama pelanggan dummy diatur di admin.

**F7. Kartu**
- Tampilan kartu debit dan kartu kredit: ORVYN Cashback dan ORVYN Voyage (travel miles).
- Blokir sementara dan buka kembali.
- Atur transaksi online, luar negeri, dan contactless.
- Atur limit harian.
- Lihat nomor kartu, CVV, dan masa berlaku setelah PIN. Data tersembunyi lagi setelah 30 detik.
- Kartu kredit: limit, tagihan berjalan, pembayaran minimum, dan bayar tagihan dari tabungan.

**F8. Notifikasi**
- Push notification untuk setiap uang masuk dan keluar, persetujuan rekening, dan promo.
- Kotak masuk di aplikasi menyimpan semua notifikasi.

**F9. Profil dan pengaturan**
- Bahasa ID/EN dan tema (ikut sistem / terang / gelap).
- Ganti PIN dan password, nyalakan atau matikan biometrik.
- Bantuan / FAQ, yang isinya diatur dari admin.
- Keluar.

**F10. ORVYN Admin (inti)**
- **Masuk admin** dengan peran: Super Admin, Operator, Konten.
- **Nasabah:**
  - Tambah, ubah, kunci, dan buka nasabah; reset PIN dan password.
  - Setujui atau tolak pendaftaran berdasarkan data yang diketik nasabah.
  - Buat nasabah dari template persona (Raka, Sinta, Pak Hendra) lengkap dengan riwayat transaksi 6 bulan. Pemilik proyek memakai ini untuk membuat 1 akun per penguji.
  - Generate nasabah dummy massal sebagai penerima transfer dan pengisi data.
- **Uang:**
  - Tambah atau kurangi saldo. Selalu tercatat sebagai transaksi, dengan alasan.
  - Simulasi uang masuk: gaji, atau transfer dari bank lain.
  - Batalkan transaksi (reversal).
- **Transaksi:** daftar lengkap, pencarian, filter, dan ringkasan harian.
- **Konten:**
  - Banner beranda (gambar, teks ID/EN, jadwal tayang).
  - FAQ dan artikel.
  - Pengumuman pop-up.
  - Kirim push ke semua nasabah, satu tier, atau satu nasabah.
- **Pengaturan:**
  - Merchant QRIS, biller, rekening bank lain dummy.
  - Batas transfer, biaya, syarat tier.
  - Fitur on/off dan mode maintenance.
- **Audit log:** siapa mengubah apa, kapan, dan dari nilai berapa ke berapa.
- **Reset demo:** kembalikan seluruh data ke kondisi awal (wajib konfirmasi dua langkah).
- **Lihat sebagai nasabah:** melihat beranda nasabah tertentu, hanya baca.

#### Fase 2: "bank yang membantu"

**F11. Vyna, asisten AI**
- Layar chat dengan jawaban yang mengalir per kata. Input bisa diketik atau diucapkan, dan Vyna bisa membacakan jawaban dengan suara perempuan.
- Pengenalan suara hanya memakai mesin yang berjalan di dalam HP (on-device), jadi rekaman suara tidak keluar dari HP. Jika HP tidak mendukung, tombol mikrofon disembunyikan.
- Bahasa mengikuti bahasa nasabah (ID/EN).
- **Yang bisa dilakukan Vyna:**
  - Cek saldo.
  - Cari transaksi ("berapa total belanja Grab bulan lalu?").
  - Ringkas pengeluaran.
  - Jelaskan produk dan FAQ.
  - **Menyiapkan** transfer atau pembayaran.
- **Batas tegas:**
  - Vyna tidak pernah mengeksekusi transaksi. Ia hanya membuka layar "periksa" yang sudah terisi, dan nasabah tetap harus memasukkan PIN.
  - Vyna hanya bisa melihat data nasabah yang sedang masuk.
  - Vyna menolak topik di luar perbankan dengan sopan.
- Ada pembatasan jumlah pesan per nasabah per hari untuk menjaga biaya API. Batasnya diatur di admin.

**F12. Insight dan anggaran**
- Transaksi dikelompokkan otomatis: makan, transportasi, belanja, tagihan, hiburan, dan lain-lain.
- Grafik pengeluaran per bulan dan per kategori, dibanding bulan lalu.
- Anggaran per kategori, dengan peringatan di 80% dan 100%.
- Kartu insight mingguan di beranda, contohnya "Makan di luar naik 18% dari bulan lalu".

**F13. Kantong tujuan**
- Buat kantong dengan nama, gambar, target nominal, dan tanggal target.
- Tabung otomatis: setiap gaji masuk, setiap minggu, atau pembulatan transaksi ke Rp10.000 terdekat.
- Pindah uang masuk dan keluar kantong secara instan.

**F14. Transfer terjadwal dan berulang** (sekali, mingguan, atau bulanan).

**F15. Minta uang dan split bill**
- Kirim permintaan ke sesama ORVYN.
- Penerima mendapat notifikasi dan bisa membayar dengan satu ketukan + PIN.

**F16. E-statement PDF bulanan.**

**F17. Widget home screen:** saldo (tersembunyi secara default) dan tombol QRIS.

#### Fase 3: "bank yang menumbuhkan"

**F18. Tier Prestige**
- Tampilan beraksen kuning.
- Kartu Relationship Manager (nama, foto, nomor) yang diatur admin.
- Kurs valas spesial.

**F19. Valas**
- Rekening USD, SGD, JPY, AUD, dan EUR.
- Tukar mata uang dengan kurs jual dan beli yang diatur admin.

**F20. Deposito online**
- Tenor 1, 3, 6, atau 12 bulan.
- Pilihan saat jatuh tempo: diperpanjang otomatis, atau dicairkan ke tabungan.

**F21. Investasi**
- Kuis profil risiko.
- Reksadana: NAB berubah setiap hari secara simulasi.
- SBN ritel.
- Tabungan emas, dengan harga jual dan beli yang diatur admin.

**F22. Dashboard total aset:** satu angka dan grafik komposisi aset.

**F23. Masuk dengan passkey.**

**Yang tidak dibuat:**
- Koneksi ke BI-FAST, QRIS, PLN, atau Dukcapil yang asli.
- Pinjaman (KTA/KPR).
- Versi iOS.
- Rilis di Play Store.

### 7.3 Teknologi

| Bagian | Pilihan |
|---|---|
| Aplikasi | Kotlin + Jetpack Compose (Material 3, tema ORVYN), CameraX + ML Kit untuk scan QR, BiometricPrompt, Credential Manager (passkey), Glance (widget) |
| Server | TypeScript (Hono) di Node.js. Satu server melayani API aplikasi dan website admin. Halaman admin dirender di server (tanpa React), jadi tidak ada build terpisah. |
| Database | PostgreSQL. Buku besar double-entry: setiap transaksi punya baris debit dan kredit, dan saldo dihitung di dalam satu transaksi database. |
| AI | Claude Haiku 5.5, dipanggil dari server dengan tool-use. API key hanya ada di server. |
| Push | Firebase Cloud Messaging (gratis). Hanya token acak untuk mengirim notifikasi. Tanpa Firebase Analytics dan Crashlytics. |
| Hosting | Oracle Cloud Always Free VM + Docker Compose + Caddy (HTTPS otomatis), subdomain gratis. Akun di-upgrade ke Pay As You Go supaya VM tidak dimatikan saat sepi, dengan peringatan budget $1. |
| Distribusi APK | APK dibangun di server ini dan dikirim manual oleh pemilik proyek (chat/Drive). Didaftarkan di akun Android Developer Console **Limited Distribution** (gratis, tanpa KTP, maks. 20 HP). Setiap penguji menyetujui sekali lewat link atau QR. |
| Keamanan minimum | HTTPS; PIN dan password di-hash; token masuk berumur pendek; batas percobaan masuk; peran admin; data kartu hanya bisa diakses pemiliknya dan admin; layar sensitif tidak bisa di-screenshot |

### 7.4 Privasi perangkat

**Prinsip:** ORVYN tidak mengambil data apa pun dari HP tempat aplikasi dipasang. Yang dikirim ke server hanya data yang **diketik sendiri** oleh pengguna di dalam aplikasi.

| Izin / data | Dipakai? | Keterangan |
|---|---|---|
| Kamera | Ya, hanya saat dibuka pengguna | Untuk scan QR (diproses di dalam HP) dan alur foto KTP/selfie (foto tidak dikirim, langsung dihapus) |
| Mikrofon | Ya, hanya saat tombol mikrofon Vyna ditekan (Fase 2) | Diproses di dalam HP, rekaman tidak dikirim |
| Galeri | Tidak ada izin | Memakai Photo Picker bawaan Android: pengguna memilih satu gambar QR, aplikasi tidak bisa melihat foto lain |
| Notifikasi | Ya | Untuk push notification |
| Biometrik | Ya | Ditangani sistem Android. Aplikasi hanya menerima hasil "cocok / tidak cocok", tidak pernah menerima sidik jari atau wajah. |
| Kontak, lokasi, SMS, telepon, file, daftar aplikasi | **Tidak** | Izinnya tidak dideklarasikan sama sekali, jadi tidak bisa diminta |
| ID perangkat (IMEI, nomor seri, Android ID, ID iklan) | **Tidak** | Pengenal "HP terdaftar" memakai kode acak yang dibuat aplikasi saat pertama dipasang |
| Analytics, pelacak, crash reporter, SDK iklan | **Tidak** | Tidak ada library pihak ketiga yang mengirim data perangkat |

**Cara memastikan:**
- Daftar izin di APK diperiksa otomatis setiap kali APK dibangun. Build gagal jika muncul izin di luar daftar ini.
- Catatan rilis yang dikirim bersama APK mencantumkan daftar izinnya, supaya tim bisa melihat sendiri.
- Di aplikasi ada halaman "Privasi" yang menjelaskan hal yang sama dengan bahasa sederhana.

### 7.5 Asumsi (belum terbukti)

| # | Asumsi | Cara memastikan | Jika salah |
|---|---|---|---|
| A2 | Kapasitas VM ARM gratis Oracle tersedia di region Singapore/Jakarta | Daftar dan upgrade ke PAYG di Fase 0 | Pakai VM AMD kecil (1GB), atau Render + Neon |
| A3 | Biaya API Vyna kecil untuk skala tim | Pantau di Anthropic Console selama Fase 2 | Turunkan batas pesan harian |
| A4 | Penguji paham bahwa ini bank fiktif | Label "Demo" kecil di layar masuk dan pengaturan | Perjelas labelnya |
| A5 | Profil nasabah OCBC (dari riset publik) mewakili pengguna ORVYN | Ambil dari sumber publik: siaran pers OCBC, FFI 2026 | Sesuaikan persona dan data dummy |
| A6 | Format QR standar QRIS bisa dibaca scanner di aplikasi | Uji di awal F5 | Pakai format QR sederhana milik ORVYN |
| A7 | APK yang dikirim manual bisa dipasang di HP penguji. Sejak 30 Sep 2026, Android di Indonesia mulai mewajibkan verifikasi developer, dan sumber resmi belum jelas apakah APK kiriman manual sudah ikut diblokir. | Di Fase 0: daftar akun Limited Distribution, kirim APK "hello world" ke 1 penguji, pasang | Penguji memakai jalur "Advanced Flow" Android (sekali saja, ada jeda 24 jam) |
| A8 | Hasil screenshot test (Roborazzi) cukup mirip dengan tampilan di HP asli | Di Fase 0: bandingkan render beranda dengan screenshot dari HP Android yang terpasang di server ini | Pengecekan di HP asli dilakukan untuk setiap layar, bukan sesekali |

## 8. Rilis

Tanggal pasti tidak ditetapkan. Setiap fase berakhir dengan APK baru yang dicoba tim, lalu survei singkat (KR5).

| Fase | Isi | Perkiraan waktu | Selesai jika |
|---|---|---|---|
| **0. Fondasi** | Model data, design system, akun Oracle (PAYG) + server + database, admin bisa masuk, akun Limited Distribution, screenshot test, APK kosong | Pendek | APK kiriman manual terpasang di HP 1 penguji, admin web bisa dibuka dari internet, render layar cocok dengan HP asli |
| **1. MVP** | F1–F10 | Paling panjang | KR1–KR5 tercapai |
| **2. Membantu** | F11–F17 | Sedang | KR6 tercapai, insight terlihat masuk akal di data dummy |
| **3. Menumbuhkan** | F18–F23 + dokumentasi portofolio | Sedang | KR7 tercapai |

Setelah PRD ini disetujui, saya lanjut ke:
1. Model data (rekening, transaksi, buku besar).
2. Pengecekan PRD dengan skill red-team untuk mencari celah.
3. User story dengan kriteria penerimaan untuk Fase 0 dan 1.

---

**Sumber riset:** [OCBC wealth management 2026](https://www.ocbc.id/id/tentang-ocbc/informasi/siaran-pers/2026/05/21/ocbc-wealth-management) · [Bisnis.com: 2% transaksi di cabang](https://finansial.bisnis.com/read/20250804/90/1899220/ocbc-ungkap-nasabah-lebih-suka-transaksi-digital-hanya-2-di-kantor-cabang) · [IDX Channel: wealth digital 44%](https://www.idxchannel.com/market-news/porsi-transaksi-wealth-digital-ocbc-naik-ke-44-persen-obligasi-digital-melonjak-50-persen/all) · [OCBC FFI 2026](https://www.ocbc.id/en/tentang-ocbc/informasi/siaran-pers/2026/09/22/financial-fitness-index-2026) · [Syarat Nyala](https://www.ocbc.id/id/syarat-dan-ketentuan/nyala) · [Fitur OCBC mobile](https://www.ocbc.id/en/article/2026/01/06/fitur-terbaru-ocbc-mobile-2025-lebih-cepat-lebih-aman-lebih-lengkap)
