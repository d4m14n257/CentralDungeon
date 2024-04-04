import express, { Router } from "express";
import { createCatalogues, getCatalogues } from "../handlers/catalogues";
import { Platforms } from "../models/models";

export const platforms : Router = express.Router();
const table_name = 'Platforms';

platforms.post('/', createCatalogues<Platforms>(table_name));
platforms.get('/:name', getCatalogues(table_name))