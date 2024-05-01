import { Request } from "express";
import multer, { FileFilterCallback } from "multer";

function fileFilter (req :Request, file : Express.Multer.File, callback : FileFilterCallback) {
    console.log(req)

    if (
        file.mimetype === 'image/png' ||
        file.mimetype === 'image/jpg' ||
        file.mimetype === 'image/jpeg' ||
        file.mimetype === 'application/pdf' ||
        file.mimetype === 'application/msword' ||
        file.mimetype === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    ) {
        callback(null, true)
    } else {
        callback(null, false)
    }
}

function destination (req :Request, file : Express.Multer.File, callback : any) {
    console.log(req)

    callback(null, 'uploads/');
}

function filename (req :Request, file : Express.Multer.File, callback : any) {
    callback(null, Date.now() + '-' + file.originalname);
}

const storage : multer.StorageEngine  = multer.diskStorage({ destination: destination, filename: filename })

export const upload = multer({
    storage: storage,
    fileFilter: fileFilter
})