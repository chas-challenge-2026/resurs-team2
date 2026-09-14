#include "resurs_audit.h"

extern "C"
{

    int resurs_audit_init(const char * /*key_file_path*/)
    {
        return RESURS_AUDIT_ERR_NOT_INIT;
    }

    int resurs_audit_chain_entry(const unsigned char * /*prev_hash*/,
                                 const char * /*entry_json*/, size_t /*entry_len*/,
                                 unsigned char * /*hash_out*/,
                                 unsigned char * /*signature_out*/, size_t * /*signature_len*/)
    {
        return RESURS_AUDIT_ERR_NOT_INIT;
    }

    int resurs_audit_verify_chain(const unsigned char * /*hashes*/,
                                  const unsigned char * /*signatures*/,
                                  const size_t * /*signature_lens*/,
                                  const char * /*entries*/, const size_t * /*entry_lens*/,
                                  size_t /*entry_count*/,
                                  const unsigned char * /*public_key*/,
                                  int * /*first_invalid_index*/)
    {
        return RESURS_AUDIT_ERR_NOT_INIT;
    }

    void resurs_audit_shutdown(void)
    {
    }

} // extern "C"
