import express, { Router } from "express";
import { conn } from "../config/database";
import { createCatalogues, getCatalogueIndex, getCatalogues, setCatalogueIndex } from "../handlers/catalogues";
import { Systems } from "../models/models";

export const systems : Router = express.Router();
const table_name = 'Systems';
const column_name = 'system_id'

systems.get('/name/:name', getCatalogues(conn, table_name));
systems.get('/index/:index', getCatalogueIndex(conn, table_name));

systems.post('/', createCatalogues<Systems>(conn, table_name));

systems.put('/index', setCatalogueIndex<Systems>(conn, table_name, column_name));