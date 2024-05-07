import express, { Router } from "express";
import { uploadFiles } from "../middleware/uploadFiles";
import { handleUploadFiles } from "../handlers/files";

export const files : Router = express.Router();

files.post('/tables/master/:table_id/:user_id', uploadFiles, handleUploadFiles());
