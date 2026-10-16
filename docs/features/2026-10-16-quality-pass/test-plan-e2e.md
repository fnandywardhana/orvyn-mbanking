# Rencana uji E2E: regresi penuh ORVYN 0.8.1

QA, 16-17 Okt 2026. Build: `main` 4f75da2 (0.8.1, versionCode 11), debug di Galaxy Note10+ lewat `adb reverse tcp:8790`, dev server lokal port 8790, admin dev (`admin@orvyn.test`). Format skenario: tujuan, kondisi awal, peran, langkah singkat, hasil yang diharapkan. Hasilnya ada di `e2e-report.md`.

**Data uji:** nomor baru `081290020001` (QA EndToEnd, dibuat lewat pendaftaran), Raka `081211112222`, Sinta `081233334444` (lewat API sebagai lawan transaksi), Hendra `081255556666` (Prestige), persona baru `081290020002` (QA Persona). PIN 482915.

**Aturan uji:** cek `mCurrentFocus` sebelum setiap input, Back tidak pernah di beranda, kamera hanya "Skip this step" atau ditolak, offline lewat `adb reverse --remove` (bukan pengaturan HP), buku besar dicek setelah setiap uang bergerak.

## 0. Otomatis
| ID | Skenario | Diharapkan |
|---|---|---|
| A1 | `cd server && npm test` | Semua lolos |
| A2 | `./gradlew testDebugUnitTest assembleDebug --rerun-tasks` | Semua lolos, APK debug 0.8.1 |
| A3 | Robolectric sementara skala 2.0, 360×640dp dan 411×869dp, terang/gelap: Unlock, Create PIN, Sign in, nominal Transfer, sukses terjadwal, baris transaksi, beranda | Tidak ada tombol/keypad/teks yang terpotong (UI-01..UI-03) |

## 1. Pendaftaran dan masuk (S1.1–S1.6, H1–H2)
| ID | Peran | Langkah | Diharapkan |
|---|---|---|---|
| R1 | Calon nasabah | Nomor baru → OTP dari notifikasi → About you dengan NIK 15 digit → Continue | Error NIK tampil dan terlihat; tidak lanjut |
| R2 | Calon nasabah | NIK yang sudah dipakai pendaftaran lain | Ditolak dengan pesan jelas, nasabah bisa memperbaikinya |
| R3 | Calon nasabah | KTP dan selfie: "Skip this step" | Lanjut tanpa foto |
| R4 | Calon nasabah | Password lemah lalu kuat; PIN `123456`; PIN tidak cocok | Kekuatan tampil; PIN urut ditolak; "Those PINs didn't match" tampil |
| R5 | Petugas | Tolak tanpa alasan, tolak dengan alasan, setujui Everyday | Tolak tanpa alasan ditolak; setuju membuat rekening + kartu debit; HP pindah ke "Your account is ready" |
| R6 | Nasabah | Sign in password salah, lalu benar | Pesan salah terlihat; masuk ke beranda; pengumuman tampil sekali |
| R7 | Nasabah | Diam 5 menit | Kembali ke layar PIN |
| R8 | Nasabah | PIN salah 5 kali, petugas unlock | "N tries left" lalu terkunci; setelah unlock bisa masuk |
| R9 | Admin/API | Password salah 5 kali; lock/unlock manual | Terkunci di percobaan ke-5; unlock memulihkan |

## 2. Beranda, rekening, riwayat (S1.7–S1.11)
| ID | Langkah | Diharapkan |
|---|---|---|
| H1 | Uang masuk dari admin, tarik untuk refresh | Saldo, Total assets, dan Recent ikut berubah; push "Money in" |
| H2 | Sembunyikan saldo, restart | Pilihan diingat |
| H3 | Ketuk kartu rekening, salin nomor | Detail rekening dan bagikan (S1.9); konfirmasi salin |
| H4 | Riwayat: cari "Kopi", filter, detail, bagikan bukti, ganti kategori | Hasil cocok; filter tanggal/jenis ada (S1.10 AC3); share sheet terbuka |
| H5 | Offline: refresh beranda, ketuk Transfer | Banner offline dengan waktu, aksi transaksi nonaktif dan terlihat nonaktif |
| H6 | Banner: geser, ketuk, konten baru dari admin | Detail promo; banner baru tampil saat beranda diperbarui (S1.36 AC3) |

## 3. Transfer (S1.12–S1.14, S2.4–S2.5)
| ID | Langkah | Diharapkan |
|---|---|---|
| T1 | ORVYN: rekening sendiri, rekening tak dikenal, Sinta | Dua yang pertama ditolak; nama Sinta muncul |
| T2 | Nominal > saldo | "You're RpX short.", tombol nonaktif |
| T3 | 50k + catatan, ketuk "Send" dua kali cepat, PIN | Satu transfer; PIN pad tanpa digit bawaan (F3); kedua pihak dapat inbox/push; ledger OK |
| T4 | Simpan, beri nama panggilan, cari, hapus penerima | Tersimpan; pencarian nama/nomor; hapus pakai konfirmasi |
| T5 | BI-FAST BCA 100k, offline di langkah PIN, online lagi | Biaya Rp2,500; pesan offline; layar review bisa dipakai lagi (F5); posting External Clearing + Fee Income |
| T6 | 100 juta Everyday | Ditolak dengan sisa limit hari ini |
| T7 | Prestige 150 juta | Pilihan BI-FAST/RTGS dengan biaya; RTGS Rp25,000 |
| T8 | Jadwal bulanan, pause/resume (juga offline), hapus, job 07:00 dua kali | Tampil di app dan admin; gagal offline ada pesan; terkirim sekali |

## 4. QRIS dan bayar (S1.15–S1.22)
| ID | Langkah | Diharapkan |
|---|---|---|
| Q1 | QRIS: tolak izin kamera; QR-ku | Pesan "Camera is off"; QR-ku dengan kecerahan penuh lalu kembali; nominal opsional (S1.16 AC1) |
| Q2 | API (Sinta): scan QR merchant, bayar PIN salah/benar, ulang kunci idempoten, nominal sangat besar | Ditolak/berhasil sekali; Merchant Settlement; limit |
| B1 | Token PLN 20k | Token 20 digit 4-4-4-4-4, salin; meter tersimpan |
| B2 | Tagihan PLN, bayar, cek lagi | "already paid"; nomor tersimpan |
| B3 | BPJS dengan PIN salah lalu benar | "Wrong PIN. 4 tries left."; lunas |
| B4 | Pulsa: nomor sendiri terisi, prefix tak dikenal, Telkomsel | Nomor sendiri jadi pilihan pertama (S1.20 AC2); pesan jelas; operator terdeteksi |
| B5 | GoPay Rp5,000 lalu 50k | Minimum dijelaskan; biaya Rp1,000 |

## 5. Kartu (S1.23–S1.26)
| ID | Langkah | Diharapkan |
|---|---|---|
| C1 | Freeze, simulasi belanja admin | Ditolak + push "declined"; kartu abu-abu |
| C2 | Online off, limit 1 juta | Online ditolak; > limit ditolak |
| C3 | Detail kartu dengan PIN | Nomor/CVV tampil, screenshot diblokir, tersembunyi setelah 30 detik |
| C4 | Hendra bayar tagihan kartu kredit 100k, juga offline | Sisa limit naik; offline ada pesan |

## 6. Fase 2 (S2.0–S2.19)
| ID | Langkah | Diharapkan |
|---|---|---|
| P1 | Kantong baru dengan target, isi sampai target (ketuk ganda), ambil > saldo, ganti nama, round-up, tutup dengan saldo | Ucapan target; satu perpindahan; pesan saldo; Auto-save Rp7,000 dari belanja Rp23,000; konfirmasi tutup |
| M1 | Raka minta ke Sinta (ketuk ganda), Sinta bayar/tolak, Raka batalkan, Sinta minta ke Raka dan dibayar dari inbox | Satu permintaan; status Paid/Declined/Cancelled; push; Pay memakai PIN |
| M2 | Split 100k dengan Sinta dan QA, ubah bagian, Sinta bayar, QA tolak; split dari riwayat | Pembulatan ditanggung pembuat; status per orang; total terisi |
| I1 | Insights, ketuk kategori, anggaran 80% | Grafik + ringkasan TalkBack; riwayat terfilter; push "Budget nearly used"; hapus anggaran pakai konfirmasi |
| V1 | Vyna 4 pertanyaan (saldo, "kirim 50 ribu ke Sinta", topik luar, makanan bulan lalu), batas harian | Angka benar; kartu Review; menolak topik luar; pesan batas |
| E1 | E-statement September | PDF dibuat di HP, share sheet terbuka |
| W1 | Widget | Tidak bisa diuji (butuh launcher) |
| F1 | Admin: matikan Pockets dan Requests, lalu nyalakan | App menyembunyikan keduanya; kembali normal |

## 7. Fase 3 (S3.1–S3.12)
| ID | Langkah | Diharapkan |
|---|---|---|
| X1 | Hendra beli JPY 1,000, jual semua, tutup rekening | Kurs Prestige; JE + FX Position; tutup pakai konfirmasi |
| D1 | Instruksi Pay out, admin Mature now (payout dan rollover), cair awal (PIN salah, offline, benar), buka 1 juta (ketuk ganda Next) | Bunga bersih benar; pesan inbox; offline ada pesan; ada konfirmasi berhasil; tenor tidak terlewati |
| N1 | Kuis risiko, beli Garuda 100k, jual 50k, ORI 1,5 juta, emas 50k | Profil tampil; unit; kelipatan 1 juta; gram |
| G1 | Wealth, RM, Prestige | Total, komposisi, kewajiban; kartu RM di beranda dan profil |
| K1 | Passkey sampai sheet sistem | Sheet Google terbuka (tanpa membuat passkey) |

## 8. Admin (S1.32–S1.38, S2.16, S3.2 AC5)
Dashboard, cari nasabah (nama/nomor/rekening), nasabah dari persona + duplikat nomor, nasabah dummy, tambah/kurangi uang (tanpa alasan, minus), tier, reversal (sekali, minus ditolak), konten (banner, FAQ), pesan + preview, maintenance, petugas dan peran, audit log dan filternya, kurs FX tercatat di audit, reset demo (hanya validasi, ditambah dry run dalam transaksi yang di-rollback).

## 9. Lintas fitur
Offline (setiap layar yang memuat atau mengirim data), PIN salah dan terkunci, uang kurang, ketukan ganda, tema terang/gelap (setelan ORVYN), font 200% (Robolectric), label TalkBack (`uiautomator dump`), buku besar setelah setiap transaksi, F3/F4/F5 dan UI-01..UI-07 dari audit CX.
