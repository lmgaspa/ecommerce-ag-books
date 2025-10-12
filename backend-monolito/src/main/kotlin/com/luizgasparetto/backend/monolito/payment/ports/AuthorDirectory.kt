package com.luizgasparetto.backend.monolito.payment.ports

import java.util.UUID

interface AuthorDirectory { fun resolvePixKey(authorId: UUID): String? }


