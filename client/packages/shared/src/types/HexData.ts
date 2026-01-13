
import { HexVector2 } from "./HexVector2";
import {Area} from "./Area";

export interface HexGrid {
  position : HexVector2;
  entryPoint? : Area;
  name: string;
  description: string;
  icon?: string;
  splashScreen?: string;
  splashScreenAudio?: string;
}