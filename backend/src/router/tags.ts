import express, { Router } from "express";
import { createCatalogues, getCatalogues } from "../handlers/catalogues";
import { Tags } from "../models/models";

export const tags : Router = express.Router();
const table_name = 'Tags';

tags.post('/', createCatalogues<Tags>('Tags'));
tags.get('/:name', getCatalogues(table_name))