import express, { Router } from "express";
import { conn } from "../config/database";
import { createCatalogues, getCatalogues } from "../handlers/catalogues";
import { Platforms } from "../models/models";

export const platforms : Router = express.Router();
const table_name = 'Platforms';

platforms.post('/', createCatalogues<Platforms>(conn, table_name));
platforms.get('/:name', getCatalogues(conn, table_name))