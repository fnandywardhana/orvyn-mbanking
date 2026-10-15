# Progres ORVYN

Terakhir diperbarui: 15 Okt 2026, sekitar 06.40 WIB. Versi online: **0.7.0** di `https://orvyn.delvault.uk`.

## Fase 0: Fondasi ✅

Semua story S0.1–S0.7 selesai (lihat riwayat commit).

## Fase 1: MVP ✅ (dengan 2 catatan)

| Fitur | Story | Status |
|---|---|---|
| Daftar dari aplikasi: OTP, data diri, foto KTP + selfie (tidak dikirim), password, PIN, menunggu persetujuan | S1.1–S1.3 | ✅ Diuji di HP. Pengambilan foto hanya teruji sampai build (saya tidak memotret ruangan Anda). |
| Petugas menyetujui/menolak pendaftaran | S1.4 | ✅ |
| Masuk pertama, ganti HP, PIN/sidik jari, keluar otomatis 5 menit | S1.5–S1.6 | ✅ Sidik jari sudah diuji pemilik di HP lain |
| Beranda, banner | S1.7–S1.8 | ✅ Banner berfoto (0.4.1), bergeser otomatis, dengan tombol ke fitur |
| Rekening, mutasi + cari + filter, detail + bagikan bukti (gambar) | S1.9–S1.11 | ✅ |
| Transfer sesama ORVYN, BI-FAST, RTGS, penerima favorit (simpan, ubah nama, hapus) | S1.12–S1.14 | ✅ |
| QRIS: scan kamera / galeri, QR-ku, merchant di admin | S1.15–S1.17 | ✅ |
| Bayar & beli: token PLN, tagihan PLN, BPJS, pulsa/data, e-wallet, biller | S1.18–S1.22 | ✅ Biller dikelola lewat database dan halaman Settings, belum ada halaman khusus biller |
| Kartu: tampilan, blokir, sakelar, limit, detail dengan PIN, tagihan kartu kredit, simulasi transaksi kartu | S1.23–S1.26 | ✅ |
| Kotak masuk, pesan petugas, pengumuman | S1.27–S1.28 | ✅ Push notification aktif sejak 0.5.0 (S2.0) |
| Tema, ganti PIN/password, bantuan, privasi | S1.29–S1.31 | ✅ |
| Admin: nasabah dari persona, nasabah dummy massal, kelola nasabah, lihat sebagai nasabah | S1.32–S1.33 | ✅ |
| Admin: tambah/kurangi uang, uang masuk realistis | S1.34 | ✅ |
| Admin: daftar transaksi, pembatalan, dashboard | S1.35 | ✅ |
| Admin: konten (banner, FAQ) | S1.36 | ✅ Banner dipilih dari pustaka foto di admin (tanpa unggah) |
| Admin: pengaturan (limit, biaya, rekening bank lain, fitur on/off, maintenance) | S1.37 | ✅ |
| Admin: petugas, audit log, reset demo | S1.38 | ✅ |

**Test otomatis:** 51 test server dan 16 test aplikasi lolos. Semua alur utama juga sudah dicoba langsung di Galaxy Note10+.

## Cara mencoba sekarang

1. Masuk ke `https://orvyn.delvault.uk/admin`.
2. Buka **Customers → New customer**, pilih persona, isi nomor HP, lalu salin pesannya ke penguji.
3. Penguji memasang APK dari link di pesan itu, lalu masuk.
4. Untuk menambah uang, buka halaman nasabah di admin, bagian **Add or remove money**.

## Fase 2: sedang dikerjakan

Story: `docs/stories/STORIES-F2.md`. Selesai: S2.0 push notification (0.5.0); S2.1–S2.3 kantong dan tabung otomatis, S2.4–S2.5 transfer terjadwal, S2.6–S2.7 minta uang dan split bill (0.6.0); S2.8–S2.11 insight dan anggaran, S2.12–S2.16 Vyna (uji 30/30), S2.17 e-statement PDF, S2.18 widget, S2.19 persona dengan kantong dan anggaran (0.7.0). **Fase 2 selesai.**

## Fitur Fase 2

Fitur: Vyna (asisten AI), insight pengeluaran, kantong tabungan, transfer terjadwal, minta uang / split bill, e-statement PDF, widget.

Vyna butuh API key Anthropic di `.env` (lihat DECISIONS-PENDING #5). Fitur lain bisa langsung dikerjakan.

## Fase 3: selesai ✅ (0.8.0)

| Fitur | Story | Status |
|---|---|---|
| Rekening valas dan tukar mata uang, kurs Prestige | S3.1–S3.2 | ✅ Diuji di HP (beli USD 1,000 dengan kurs Prestige) |
| Deposito: buka, jatuh tempo, perpanjang atau cair, cair lebih awal, "Mature now" di admin | S3.3–S3.4 | ✅ Diuji di HP |
| Profil risiko, reksadana (NAB harian simulasi), SBN (kupon bulanan), emas | S3.5–S3.8 | ✅ Diuji di HP |
| Layar Wealth (total aset dan komposisi) | S3.9 | ✅ Diuji di HP, terang dan gelap |
| Tampilan Prestige dan kartu RM (avatar monogram) | S3.10–S3.11 | ✅ Diuji di HP |
| Passkey | S3.12 | ⚠️ Server teruji dengan authenticator software. Pembuatan passkey di HP belum diuji (menunggu izin pemilik) |
| README dan cerita proyek | S3.13 | ✅ `README.md`, `docs/STORY.md`. Repo belum di GitHub (menunggu pemilik) |

**Test otomatis:** 92 test server, test aplikasi, dan evaluasi Vyna 30/30.
