// Collection
const col = db.getCollection("s_assets");

// Preview
//print("Preview (before):");
//col.find({ path: { $regex: "/" } }, { path: 1 })
//  .limit(10)
//  .forEach(printjson);

// Update
const res = col.updateMany(
  { path: { $type: "string", $regex: "/" } },
  [
    {
      $set: {
        path: {
          $substrBytes: [
            "$path",
            { $add: [{ $indexOfBytes: ["$path", "/"] }, 1] },
            { $strLenBytes: "$path" }
          ]
        }
      }
    }
  ]
);

print("Update result:");
printjson(res);

// Preview after
//print("Preview (after):");
//col.find({ path: { $regex: "/" } }, { path: 1 })
//  .limit(10)
//  .forEach(printjson);