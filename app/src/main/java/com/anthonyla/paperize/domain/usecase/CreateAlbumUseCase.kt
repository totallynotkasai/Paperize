package com.anthonyla.paperize.domain.usecase

import com.anthonyla.paperize.core.Result
import com.anthonyla.paperize.domain.model.Album
import com.anthonyla.paperize.domain.repository.AlbumRepository
import javax.inject.Inject

class CreateAlbumUseCase @Inject constructor(
    private val albumRepository: AlbumRepository
) {
    suspend operator fun invoke(name: String, coverUri: String? = null): Result<Album> = Result.runCatching {
        require(name.isNotBlank()) { "Album name cannot be empty" }
        // Same rule as renaming: names are unique regardless of case.
        require(!albumRepository.isAlbumNameTaken(name)) { "Album with this name already exists" }
        albumRepository.createAlbum(name, coverUri).getOrThrow()
    }
}
