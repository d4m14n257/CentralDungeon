import { NextFunction, Request, Response } from "express";
import multer, { FileFilterCallback } from "multer";
import fs from 'node:fs';

function fileFilter (req :Request, file : Express.Multer.File, callback : FileFilterCallback) {
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
        callback(new Error ('Invalid mime type'))
    }
}

async function destination (req :Request, file : Express.Multer.File, callback : any) {
    if(!fs.existsSync(`${process.env.UPLOAD_FILES}`)) {
        await fs.mkdirSync(`${process.env.UPLOAD_FILES}`);
    }

    const user_id = req.params.user_id;
    const folder = `${process.env.UPLOAD_FILES}/${user_id}`

    if(!fs.existsSync(folder))
        await fs.mkdirSync(folder);

    callback(null, folder);
}

function filename (req :Request, file : Express.Multer.File, callback : any) {
    callback(null, Date.now() + '-' + file.originalname);
}

const storage : multer.StorageEngine  = multer.diskStorage({ destination: destination, filename: filename })

const upload = multer({
    storage: storage,
    fileFilter: fileFilter
})

export const uploadFiles = (req : Request, res : Response, next : NextFunction) => {
    upload.array('files')(req, res, (err) => {
        if(err) {
            if(err.message == 'Invalid mime type')
                return res.status(415).send({ error: err.message });

            return res.status(400).send({ error: err.message });
        }

        next();
    })
}