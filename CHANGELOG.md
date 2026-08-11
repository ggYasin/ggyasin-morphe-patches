# 1.2.0

- Add an opt-in Offline Games 3.14.1 ARMv7 patch that changes the built-in house-ad countdown from 15 seconds to 1.
- Verify the exact native library and instruction signature before modifying `libil2cpp.so`.

# 1.1.2

- Restore ZenSMS's original OTP handler and remove the custom runtime extractor.
- Add only universal and Persian OTP patterns directly to the stock extractor.
- Preserve the stock candidate validator and extend its nearby-context ignore terms with `تخفیف`.

# 1.1.1

- Fix on-device OTP hook application by emitting branch instructions directly.

# 1.1.0

- Add OTPHelper-first OTP detection with the original ZenSMS detector as fallback.
- Add hard-ignore handling and optional patch-time phrase lists.
- Select the RTL patch by default and share one extension payload between patches.
