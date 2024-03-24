import express, { Router } from "express";
import { conn } from "../config/database";
import { createCatalogues, getCatalogues } from "../handlers/catalogues";
import { Tags } from "../models/models";

export const tags : Router = express.Router();
const table_name = 'Tags';

tags.post('/', createCatalogues<Tags>(conn, 'Tags'));
tags.get('/:name', getCatalogues(conn, table_name))