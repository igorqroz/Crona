/**
 * A versão do Crona (SemVer: MAIOR.MENOR.CORREÇÃO), lida do package.json na hora do build.
 *
 * Fonte da verdade: a "version" do frontend/package.json e a <version> do backend/pom.xml
 * mudam JUNTAS a cada versão nova (o CI confere). O que mudou em cada uma está no CHANGELOG.md.
 * Importar só o campo "version": o build leva só ele, não o package.json inteiro.
 */
import { version } from '../../../package.json';

export const VERSAO = version;
