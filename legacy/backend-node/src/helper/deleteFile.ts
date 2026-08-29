import fs from 'node:fs';

export default async function deleteFile (file : Express.Multer.File) {
    try {
        await fs.unlinkSync(file.path)
    }
    catch {
        return {status: 418, message: 'Error to delete files.'}
    }
}